/* Copyright 2002-2018 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.gnss.attitude;

import org.hipparchus.analysis.differentiation.DerivativeStructure;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.errors.OrekitException;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeStamped;
import org.orekit.utils.FieldPVCoordinates;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedAngularCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * Boilerplate computations for GNSS attitude.
 *
 * <p>
 * This class is intended to hold throw-away data pertaining to <em>one</em> call
 * to {@link GNSSAttitudeProvider#getAttitude(org.orekit.utils.PVCoordinatesProvider,
 * org.orekit.time.AbsoluteDate, org.orekit.frames.Frame) getAttitude}. It allows
 * the various {@link GNSSAttitudeProvider} implementations to be immutable as they
 * do not store any state, and hence to be thread-safe, reentrant and naturally
 * serializable (so for example ephemeris built from them are also serializable).
 * </p>
 *
 * @author Luc Maisonobe
 * @since 9.2
 */
class GNSSAttitudeContext implements TimeStamped {

    /** Constant Y axis. */
    private static final PVCoordinates PLUS_Y =
            new PVCoordinates(Vector3D.PLUS_J, Vector3D.ZERO, Vector3D.ZERO);

    /** Constant Z axis. */
    private static final PVCoordinates MINUS_Z =
            new PVCoordinates(Vector3D.MINUS_K, Vector3D.ZERO, Vector3D.ZERO);

    /** Limit value below which we shoud use replace beta by betaIni. */
    private static final double BETA_SIGN_CHANGE_PROTECTION = FastMath.toRadians(0.07);

    /** Derivation order. */
    private static final int ORDER = 2;

    /** Indicator for half orbit from orbital noon to orbital midnight or the other way round. */
    private final double towardsEclipse;

    /** Spacecraft position-velocity in inertial frame. */
    private final TimeStampedPVCoordinates svPV;

    /** Spacecraft position-velocity in inertial frame. */
    private final FieldPVCoordinates<DerivativeStructure> svPVDS;

    /** Angle between Sun and orbital plane. */
    private final DerivativeStructure beta;

    /** Cosine of the angle between spacecraft and Sun direction. */
    private final DerivativeStructure svbCos;

    /** Nominal yaw. */
    private final TimeStampedAngularCoordinates nominalYaw;

    /** Nominal yaw. */
    private final FieldRotation<DerivativeStructure> nominalYawDS;

    /** Spacecraft angular velocity. */
    private double muRate;

    /** Limit cosine for the midnight turn. */
    private double cNight;

    /** Limit cosine for the noon turn. */
    private double cNoon;

    /** Relative orbit angle to turn center. */
    private double delta;

    /** Half span of the turn region, as an angle in orbit plane. */
    private double halfSpan;

    /** Turn start date. */
    private AbsoluteDate turnStart;

    /** Turn end date. */
    private AbsoluteDate turnEnd;

    /** Simple constructor.
     * @param sunPV Sun position-velocity in inertial frame
     * @param svPV spacecraft position-velocity in inertial frame
     * @exception OrekitException if yaw cannot be corrected
     */
    GNSSAttitudeContext(final TimeStampedPVCoordinates sunPV, final TimeStampedPVCoordinates svPV)
        throws OrekitException {

        this.towardsEclipse = -Vector3D.dotProduct(sunPV.getPosition(), svPV.getVelocity());
        this.svPV           = svPV;
        final FieldPVCoordinates<DerivativeStructure> sunPVDS = sunPV.toDerivativeStructurePV(ORDER);
        this.svPVDS  = svPV.toDerivativeStructurePV(ORDER);
        this.svbCos  = FieldVector3D.dotProduct(sunPVDS.getPosition(), svPVDS.getPosition()).
                       divide(sunPVDS.getPosition().getNorm().
                              multiply(svPVDS.getPosition().getNorm()));
        this.beta    = FieldVector3D.angle(sunPVDS.getPosition(), svPVDS.getMomentum()).
                       negate().
                       add(0.5 * FastMath.PI);

        // nominal yaw steering
        this.nominalYaw =
                        new TimeStampedAngularCoordinates(svPV.getDate(),
                                                          svPV.normalize(),
                                                          PVCoordinates.crossProduct(sunPV, svPV).normalize(),
                                                          MINUS_Z,
                                                          PLUS_Y,
                                                          1.0e-9);
        this.nominalYawDS = nominalYaw.toDerivativeStructureRotation(ORDER);

        // TODO: the Kouba model assumes perfectly circular orbit, it should really be:
        // this.muRate = svPV.getAngularVelocity();
        this.muRate = svPV.getVelocity().getNorm() / svPV.getPosition().getNorm();

    }

    /** {@inheritDoc} */
    @Override
    public AbsoluteDate getDate() {
        return svPV.getDate();
    }

    /** Get the cosine of the angle between spacecraft and Sun direction.
     * @return cosine of the angle between spacecraft and Sun direction.
     */
    public double getSVBcos() {
        return svbCos.getValue();
    }

    /** Get the angle between Sun and orbital plane.
     * @return angle between Sun and orbital plane
     * @see #getSecuredBeta(TurnTimeRange)
     */
    public double getBeta() {
        return beta.getValue();
    }

    /** Get a Sun elevation angle that does not change sign within the turn.
     * <p>
     * This method either returns the current beta or replaces it with the
     * value at turn start, so the sign remains constant throughout the
     * turn. As of 9.2, it is only useful for GPS and Glonass.
     * </p>
     * @return secured Sun elevation angle
     * @see #getBeta()
     */
    public double getSecuredBeta() {
        return FastMath.abs(beta.getReal()) < BETA_SIGN_CHANGE_PROTECTION ?
               beta.taylor(-timeSinceTurnStart(getDate())) :
               getBeta();
    }

    /** Get the nominal yaw.
     * @return nominal yaw
     */
    public TimeStampedAngularCoordinates getNominalYaw() {
        return nominalYaw;
    }

    /** Compute nominal yaw angle.
     * @return nominal yaw angle
     */
    public double yawAngle() {
        final Vector3D xSat = nominalYaw.getRotation().revert().applyTo(Vector3D.PLUS_I);
        return FastMath.copySign(Vector3D.angle(svPV.getVelocity(), xSat), -beta.getReal());
    }

    /** Compute nominal yaw angle.
     * @return nominal yaw angle
     */
    public DerivativeStructure yawAngleDS() {
        final FieldVector3D<DerivativeStructure> xSat = nominalYawDS.revert().applyTo(Vector3D.PLUS_I);
        return FastMath.copySign(FieldVector3D.angle(svPV.getVelocity(), xSat), -beta.getReal());
    }

    /** Set up the midnight/noon turn region.
     * @param cosNight limit cosine for the midnight turn
     * @param cosNoon limit cosine for the noon turn
     * @return true if spacecraft is in the midnight/noon turn region
     */
    public boolean setUpTurnRegion(final double cosNight, final double cosNoon) {
        this.cNight = cosNight;
        this.cNoon  = cosNoon;
        if (svbCos.getValue() < cNight) {
            // in eclipse turn mode
            delta = FastMath.copySign(inOrbitPlaneAngle(FastMath.PI - FastMath.acos(svbCos.getValue())),
                                      towardsEclipse);
            return true;
        } else if (svbCos.getValue() > cNoon) {
            // in noon turn mode
            delta = FastMath.copySign(inOrbitPlaneAngle(FastMath.acos(svbCos.getValue())),
                                      -towardsEclipse);
            return true;
        } else {
            return false;
        }
    }

    /** Get the relative orbit angle to turn center.
     * @return relative orbit angle to turn center
     */
    public double getDelta() {
        return delta;
    }

    /** Check if spacecraft is in the half orbit closest to Sun.
     * @return true if spacecraft is in the half orbit closest to Sun
     */
    public boolean inSunSide() {
        return svbCos.getValue() > 0;
    }

    /** Get yaw at turn start.
     * @param sunBeta Sun elevation above orbital plane
     * (it <em>may</em> be different from {@link #getBeta()} in
     * some special cases)
     * @return yaw at turn start
     */
    public double getYawStart(final double sunBeta) {
        return computePhi(sunBeta, FastMath.copySign(halfSpan, svbCos.getValue()));
    }

    /** Get yaw at turn end.
     * @param sunBeta Sun elevation above orbital plane
     * (it <em>may</em> be different from {@link #getBeta()} in
     * some special cases)
     * @return yaw at turn end
     */
    public double getYawEnd(final double sunBeta) {
        return computePhi(sunBeta, -FastMath.copySign(halfSpan, svbCos.getValue()));
    }

    /** Compute yaw rate.
     * @param sunBeta Sun elevation above orbital plane
     * (it <em>may</em> be different from {@link #getBeta()} in
     * some special cases)
     * @return yaw rate
     */
    public double yawRate(final double sunBeta) {
        return (getYawEnd(sunBeta) - getYawStart(sunBeta)) / getTurnDuration();
    }

    /** Get the spacecraft angular velocity.
     * @return spacecraft angular velocity
     */
    public double getMuRate() {
        return muRate;
    }

    /** Project a spacecraft/Sun angle into orbital plane.
     * <p>
     * This method is intended to find the limits of the noon and midnight
     * turns in orbital plane. The return angle is always positive. The
     * {@link #towardsEclipse()} method must be called to find the correct
     * sign to apply
     * </p>
     * @param angle spacecraft/Sun angle (or spacecraft/opposite-of-Sun)
     * @return angle projected into orbital plane, always positive
     */
    public DerivativeStructure inOrbitPlaneAngle(final DerivativeStructure angle) {
        // TODO: the Kouba model assumes planar right-angle triangle resolution, it should really be:
//        return FastMath.acos(FastMath.cos(angle).divide(FastMath.cos(beta)));
        return angle.multiply(angle).subtract(beta.multiply(beta)).sqrt();
    }

    /** Project a spacecraft/Sun angle into orbital plane.
     * <p>
     * This method is intended to find the limits of the noon and midnight
     * turns in orbital plane. The return angle is always positive. The
     * {@link #towardsEclipse()} method must be called to find the correct
     * sign to apply
     * </p>
     * @param angle spacecraft/Sun angle (or spacecraft/opposite-of-Sun)
     * @return angle projected into orbital plane, always positive
     */
    public double inOrbitPlaneAngle(final double angle) {
        // TODO: the Kouba model assumes planar right-angle triangle resolution, it should really be:
        // return FastMath.acos(FastMath.cos(angle) / FastMath.cos(beta.getReal()));
        return FastMath.sqrt(angle * angle - beta.getReal() * beta.getReal());
    }

    /** Compute yaw.
     * @param sunBeta Sun elevation above orbital plane
     * (it <em>may</em> be different from {@link #getBeta()} in
     * some special cases)
     * @param inOrbitPlaneAngle in orbit angle between spacecraft
     * and Sun (or opposite of Sun) projection
     * @return yaw angle
     */
    public double computePhi(final double sunBeta, final double inOrbitPlaneAngle) {
        return FastMath.atan2(-FastMath.tan(sunBeta), FastMath.sin(inOrbitPlaneAngle));
    }

    /** Set turn half span and compute corresponding turn time range.
     * @param halfSpan half span of the turn region, as an angle in orbit plane
     */
    public void setHalfSpan(final double halfSpan) {
        this.halfSpan  = halfSpan;
        this.turnStart = svPV.getDate().shiftedBy((delta - halfSpan) / muRate);
        this.turnEnd   = svPV.getDate().shiftedBy((delta + halfSpan) / muRate);
    }

    /** Check if a date is within range.
     * @param date date to check
     * @param endMargin margin in seconds after turn end
     * @return true if date is within range extended by end margin
     */
    public boolean inTurnTimeRange(final AbsoluteDate date, final double endMargin) {
        return date.durationFrom(turnStart) > 0 &&
               date.durationFrom(turnEnd)   < endMargin;
    }

    /** Get turn duration.
     * @return turn duration
     */
    public double getTurnDuration() {
        return 2 * halfSpan / muRate;
    }

    /** Get elapsed time since turn start.
     * @param date date to check
     * @return elapsed time from turn start to specified date
     */
    public double timeSinceTurnStart(final AbsoluteDate date) {
        return date.durationFrom(turnStart);
    }

    /** Generate an attitude with turn-corrected yaw.
     * @param yaw yaw value to apply
     * @param yawDot yaw first time derivative
     * @return attitude with specified yaw
     * @exception OrekitException if zero yaw derivatives cannot be computed
     */
    public TimeStampedAngularCoordinates turnCorrectedAttitude(final double yaw, final double yawDot)
        throws OrekitException {

        // compute a linear yaw correction model
        final DerivativeStructure nominalAngle   = yawAngleDS();
        final DerivativeStructure correctedAngle = nominalAngle.getFactory().build(yaw, yawDot, 0.0);
        final TimeStampedAngularCoordinates correction =
                        new TimeStampedAngularCoordinates(nominalYaw.getDate(),
                                                          new FieldRotation<>(FieldVector3D.getPlusK(nominalAngle.getField()),
                                                                              nominalAngle.subtract(correctedAngle),
                                                                              RotationConvention.FRAME_TRANSFORM));

        // combine the two parts of the attitude
        return correction.addOffset(getNominalYaw());

    }

    /** Compute Orbit Normal (ON) yaw.
     * @return Orbit Normal yaw, using inertial frame as reference
     * @exception OrekitException if derivation order is too large (never happens with hard-coded order)
     */
    public TimeStampedAngularCoordinates orbitNormalYaw()
        throws OrekitException {
        final PVCoordinates normal = new PVCoordinates(svPVDS.getMomentum());
        return new TimeStampedAngularCoordinates(svPV.getDate(),
                                                 MINUS_Z, svPV.normalize(),
                                                 PLUS_Y, normal.negate(),
                                                 1.0e-9);
    }

}

/* Copyright 2002-2010 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
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
package org.orekit.attitudes;

import org.apache.commons.math.geometry.Rotation;
import org.apache.commons.math.geometry.Vector3D;
import org.orekit.errors.OrekitException;
import org.orekit.orbits.Orbit;
import org.orekit.utils.PVCoordinates;


/**
 * This class handles yaw compensation attitude law.

 * <p>
 * Yaw compensation is mainly used for Earth observation satellites. As a
 * satellites moves along its track, the image of ground points move on
 * the focal point of the optical sensor. This motion is a combination of
 * the satellite motion, but also on the Earth rotation and on the current
 * attitude (in particular if the pointing includes Roll or Pitch offset).
 * In order to reduce geometrical distortion, the yaw angle is changed a
 * little from the simple ground pointing attitude such that the apparent
 * motion of ground points is along a prescribed axis (orthogonal to the
 * optical sensors rows), taking into account all effects.
 * </p>
 * <p>
 * This attitude is implemented as a wrapper on top of an underlying ground
 * pointing law that defines the roll and pitch angles.
 * </p>
 * <p>
 * Instances of this class are guaranteed to be immutable.
 * </p>
 * @see     GroundPointing
 * @author V&eacute;ronique Pommier-Maurussane
 * @version $Revision:1665 $ $Date:2008-06-11 12:12:59 +0200 (mer., 11 juin 2008) $
 */
public class YawCompensation extends GroundPointingWrapper {

    /** Serializable UID. */
    private static final long serialVersionUID = 1145977506851433023L;

    /** Creates a new instance.
     * @param groundPointingLaw ground pointing attitude law without yaw compensation
     */
    public YawCompensation(final GroundPointing groundPointingLaw) {
        super(groundPointingLaw);
    }

    /** {@inheritDoc} */
    public Rotation getCompensation(final Orbit orbit, final Attitude base)
        throws OrekitException {

        // Compensation rotation definition :
        //  . Z satellite axis is unchanged
        //  . X satellite axis shall be aligned to target relative velocity
        final PVCoordinates targetPV    = getGroundPointingLaw().getTargetPV(orbit, orbit.getFrame());
        final Vector3D targetVelocity   = targetPV.getVelocity();
        final Vector3D satVelocity      = orbit.getPVCoordinates().getVelocity();
        final Vector3D relativeVelocity = targetVelocity.subtract(satVelocity);

        // Create rotation transforming zsat to zsat and relativeVelocity to -xsat
        final Rotation compensation =
            new Rotation(Vector3D.PLUS_K, base.getRotation().applyTo(relativeVelocity),
                         Vector3D.PLUS_K, Vector3D.MINUS_I);

        return compensation;

    }

    /** Compute the yaw compensation angle at date.
     * @param orbit orbit state
     * @return yaw compensation angle for orbit.
     * @throws OrekitException if some specific error occurs
     */
    public double getYawAngle(final Orbit orbit)
        throws OrekitException {

        // Attitude rotation without yaw compensation
        final Rotation rotNoYaw = getGroundPointingLaw().getState(orbit).getRotation();

        // Attitude rotation without yaw compensation
        final Rotation rotYaw = getState(orbit).getRotation();

        // Compute yaw compensation angle by composition of both rotations
        final Rotation compoRot = rotYaw.applyTo(rotNoYaw.revert());
        final double yawAngle = compoRot.getAngle();

        return yawAngle;

    }

}

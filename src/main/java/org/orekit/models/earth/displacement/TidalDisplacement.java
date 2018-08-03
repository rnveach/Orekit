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
package org.orekit.models.earth.displacement;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.data.BodiesElements;
import org.orekit.data.PoissonSeries.CompiledSeries;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinatesProvider;

/**
 * Modeling of displacement of reference points due to tidal effects.
 * <p>
 * This class implements displacement of reference point (i.e.
 * {@link org.orekit.estimation.measurements.GroundStation ground stations})
 * due to tidal effects, as per IERS conventions.
 * </p>
 * <p>
 * Displacement can be computed with respect to either <em>conventional tide free</em>
 * or <em>mean tide</em> coordinates. The difference between the two systems is
 * about -12cm at poles and +6cm at equator. Selecting one system or the other
 * depends on how the station coordinates have been computed (i.e. it depends
 * whether the coordinates already include the permanent deformation or not).
 * </p>
 * <p>
 * Instances of this class are guaranteed to be immutable
 * </p>
 * @see org.orekit.estimation.measurements.GroundStation
 * @since 9.1
 * @author Luc Maisonobe
 */
public class TidalDisplacement implements StationDisplacement {

    /** Sun motion model. */
    private final PVCoordinatesProvider sun;

    /** Moon motion model. */
    private final PVCoordinatesProvider moon;

    /** Flag for permanent deformation. */
    private final boolean removePermanentDeformation;

    /** Ratio for degree 2 tide generated by Sun. */
    private final double ratio2S;

    /** Ratio for degree 3 tide generated by Sun. */
    private final double ratio3S;

    /** Ratio for degree 2 tide generated by Moon. */
    private final double ratio2M;

    /** Ratio for degree 3 tide generated by Moon. */
    private final double ratio3M;

    /** Displacement Shida number h⁽⁰⁾. */
    private final double hSup0;

    /** Displacement Shida number h⁽²⁾. */
    private final double hSup2;

    /** Displacement Shida number h₃. */
    private final double h3;

    /** Displacement Shida number hI diurnal. */
    private final double hIDiurnal;

    /** Displacement Shida number hI semi-diurnal. */
    private final double hISemiDiurnal;

    /** Displacement Love number l⁽⁰⁾. */
    private final double lSup0;

    /** Displacement Love number l⁽¹⁾ diurnal. */
    private final double lSup1Diurnal;

    /** Displacement Love number l⁽¹⁾ semi-diurnal. */
    private final double lSup1SemiDiurnal;

    /** Displacement Love number l⁽²⁾. */
    private final double lSup2;

    /** Displacement Love number l₃. */
    private final double l3;

    /** Displacement Love number lI diurnal. */
    private final double lIDiurnal;

    /** Displacement Love number lI semi-diurnal. */
    private final double lISemiDiurnal;

    /** Permanent deformation amplitude. */
    private final double h0Permanent;

    /** Function computing corrections in the frequency domain for diurnal tides.
     * <ul>
     *  <li>f[0]: radial correction, longitude cosine part</li>
     *  <li>f[1]: radial correction, longitude sine part</li>
     *  <li>f[2]: North correction, longitude cosine part</li>
     *  <li>f[3]: North correction, longitude sine part</li>
     *  <li>f[4]: East correction, longitude cosine part</li>
     *  <li>f[5]: East correction, longitude sine part</li>
     * </ul>
     */
    private final CompiledSeries frequencyCorrectionDiurnal;

    /** Function computing corrections in the frequency domain for zonal tides.
     * <ul>
     *  <li>f[0]: radial correction</li>
     *  <li>f[1]: North correction, longitude cosine part</li>
     * </ul>
     */
    private final CompiledSeries frequencyCorrectionZonal;

    /** Simple constructor.
     * @param rEarth Earth equatorial radius (from gravity field model)
     * @param sunEarthSystemMassRatio Sun/(Earth + Moon) mass ratio
     * (typically {@link org.orekit.utils.Constants#JPL_SSD_SUN_EARTH_PLUS_MOON_MASS_RATIO Constants.JPL_SSD_SUN_EARTH_PLUS_MOON_MASS_RATIO})
     * @param earthMoonMassRatio Earth/Moon mass ratio
     * (typically {@link org.orekit.utils.Constants#JPL_SSD_EARTH_MOON_MASS_RATIO Constants.JPL_SSD_EARTH_MOON_MASS_RATIO})
     * @param sun Sun model
     * @param moon Moon model
     * @param conventions IERS conventions to use
     * @param removePermanentDeformation if true, the station coordinates are
     * considered <em>mean tide</em> and already include the permanent deformation, hence
     * it should be removed from the displacement to avoid considering it twice;
     * if false, the station coordinates are considered <em>conventional tide free</em>
     * so the permanent deformation must be included in the displacement
     * @see org.orekit.frames.FramesFactory#getITRF(IERSConventions, boolean)
     * @see org.orekit.frames.FramesFactory#getEOPHistory(IERSConventions, boolean)
     * @see org.orekit.utils.Constants#JPL_SSD_SUN_EARTH_PLUS_MOON_MASS_RATIO
     * @see org.orekit.utils.Constants#JPL_SSD_EARTH_MOON_MASS_RATIO
          */
    public TidalDisplacement(final double rEarth,
                             final double sunEarthSystemMassRatio,
                             final double earthMoonMassRatio,
                             final PVCoordinatesProvider sun, final PVCoordinatesProvider moon,
                             final IERSConventions conventions,
                             final boolean removePermanentDeformation)
        {

        final double sunEarthMassRatio = sunEarthSystemMassRatio * (1 + 1 / earthMoonMassRatio);
        final double moonEarthMassRatio = 1.0 / earthMoonMassRatio;

        this.sun                         = sun;
        this.moon                        = moon;
        this.removePermanentDeformation = removePermanentDeformation;

        final double r2 = rEarth * rEarth;
        final double r4 = r2 * r2;
        this.ratio2S    = r4 * sunEarthMassRatio;
        this.ratio3S    = ratio2S * rEarth;
        this.ratio2M    = r4 * moonEarthMassRatio;
        this.ratio3M    = ratio2M * rEarth;

        // Get the nominal values for the Love and Shiva numbers
        final double[] hl = conventions.getNominalTidalDisplacement();
        hSup0            = hl[ 0];
        hSup2            = hl[ 1];
        h3               = hl[ 2];
        hIDiurnal        = hl[ 3];
        hISemiDiurnal    = hl[ 4];
        lSup0            = hl[ 5];
        lSup1Diurnal     = hl[ 6];
        lSup1SemiDiurnal = hl[ 7];
        lSup2            = hl[ 8];
        l3               = hl[ 9];
        lIDiurnal        = hl[10];
        lISemiDiurnal    = hl[11];
        h0Permanent      = hl[12];

        this.frequencyCorrectionDiurnal = conventions.getTidalDisplacementFrequencyCorrectionDiurnal();
        this.frequencyCorrectionZonal   = conventions.getTidalDisplacementFrequencyCorrectionZonal();

    }

    /** {@inheritDoc} */
    @Override
    public Vector3D displacement(final BodiesElements elements, final Frame earthFrame, final Vector3D referencePoint)
        {

        final AbsoluteDate date = elements.getDate();

        // preliminary computation (we hold everything in local variables so method is thread-safe)
        final PointData      pointData    = new PointData(referencePoint);
        final Vector3D       sunPosition  = sun.getPVCoordinates(date, earthFrame).getPosition();
        final BodyData       sunData      = new BodyData(sunPosition, ratio2S, ratio3S, pointData);
        final Vector3D       moonPosition = moon.getPVCoordinates(date, earthFrame).getPosition();
        final BodyData       moonData     = new BodyData(moonPosition, ratio2M, ratio3M, pointData);

        // step 1 in IERS procedure: corrections in the time domain
        Vector3D displacement = timeDomainCorrection(pointData, sunData, moonData);

        // step 2 in IERS procedure: corrections in the frequency domain
        displacement = displacement.add(frequencyDomainCorrection(elements, pointData));

        if (removePermanentDeformation) {
            // the station coordinates already include permanent deformation,
            // so it should not be included in the displacement that will be
            // added to these coordinates to avoid considering this deformation twice
            // as step 1 did include permanent deformation, we remove it here
            displacement = displacement.subtract(permanentDeformation(pointData));
        }

        return displacement;

    }

    /** Compute the corrections in the time domain (step 1 in IERS procedure).
     * @param pointData reference point data
     * @param sunData Sun data
     * @param moonData Moon data
     * @return displacement of the reference point
          */
    private Vector3D timeDomainCorrection(final PointData pointData,
                                          final BodyData sunData, final BodyData moonData)
        {

        final double h2  = hSup0 + hSup2 * pointData.f;
        final double l2  = lSup0 + lSup2 * pointData.f;

        // in-phase, degree 2 (equation 7.5 in IERS conventions 2010)
        final double s2R = sunData.factor2  * 3.0 * l2 * sunData.dot;
        final double s2r = sunData.factor2  * 0.5 * h2 * (3 * sunData.dot2 - 1) - s2R * sunData.dot;
        final double m2R = moonData.factor2 * 3.0 * l2 * moonData.dot;
        final double m2r = moonData.factor2 * 0.5 * h2 * (3 * moonData.dot2 - 1) - m2R * moonData.dot;

        // in-phase, degree 3 (equation 7.6 in IERS conventions 2010)
        final double s3R = sunData.factor3  * l3 * (7.5 * sunData.dot2 - 1.5);
        final double s3r = sunData.factor3  * h3 * sunData.dot * (2.5 * sunData.dot2 - 1.5) - s3R * sunData.dot;
        final double m3R = moonData.factor3 * l3 * (7.5 * moonData.dot2 - 1.5);
        final double m3r = moonData.factor3 * h3 * moonData.dot * (2.5 * moonData.dot2 - 1.5) - m3R * moonData.dot;

        // combine contributions along radial, Sun and Moon directions
        final Vector3D inPhaseDisplacement = new Vector3D(s2r + m2r + s3r + m3r,    pointData.radial,
                                                          (s2R + s3R) / sunData.r,  sunData.position,
                                                          (m2R + m3R) / moonData.r, moonData.position);

        // out-of-phase, degree 2, diurnal tides (equations 7.10a and 7.10b in IERS conventions 2010)
        final double drOd = -0.75 * hIDiurnal * pointData.sin2Phi *
                            (sunData.factor2  * sunData.sin2Phi  * sunData.sinDeltaLambda +
                             moonData.factor2 * moonData.sin2Phi * moonData.sinDeltaLambda);
        final double dnOd = -1.5 * lIDiurnal * pointData.cos2Phi *
                            (sunData.factor2  * sunData.sin2Phi  * sunData.sinDeltaLambda +
                             moonData.factor2 * moonData.sin2Phi * moonData.sinDeltaLambda);
        final double deOd = -1.5 * lIDiurnal * pointData.sinPhi *
                            (sunData.factor2  * sunData.sin2Phi  * sunData.cosDeltaLambda +
                             moonData.factor2 * moonData.sin2Phi * moonData.cosDeltaLambda);

        // out-of-phase, degree 2, semi-diurnal tides (equation 7.11 in IERS conventions 2010)
        final double drOsd = -0.75 * hISemiDiurnal * pointData.cosPhi2 *
                             (sunData.factor2  * sunData.cosPhi2  * sunData.sin2DeltaLambda +
                              moonData.factor2 * moonData.cosPhi2 * moonData.sin2DeltaLambda);
        final double dnOsd = 0.75 * lISemiDiurnal * pointData.sin2Phi *
                             (sunData.factor2  * sunData.cosPhi2  * sunData.sin2DeltaLambda +
                              moonData.factor2 * moonData.cosPhi2 * moonData.sin2DeltaLambda);
        final double deOsd = -1.5 * lISemiDiurnal * pointData.cosPhi *
                             (sunData.factor2  * sunData.cosPhi2  * sunData.cos2DeltaLambda +
                              moonData.factor2 * moonData.cosPhi2 * moonData.cos2DeltaLambda);

        // latitude dependency, diurnal tides (equation 7.8 in IERS conventions 2010)
        final double dnLd = -lSup1Diurnal * pointData.sinPhi2 *
                            (sunData.factor2  * sunData.p21  * sunData.cosDeltaLambda +
                             moonData.factor2 * moonData.p21 * moonData.cosDeltaLambda);
        final double deLd =  lSup1Diurnal * pointData.sinPhi * pointData.cos2Phi *
                            (sunData.factor2  * sunData.p21  * sunData.sinDeltaLambda +
                             moonData.factor2 * moonData.p21 * moonData.sinDeltaLambda);

        // latitude dependency, semi-diurnal tides (equation 7.9 in IERS conventions 2010)
        final double dnLsd = -0.25 * lSup1SemiDiurnal * pointData.sin2Phi *
                             (sunData.factor2  * sunData.p22  * sunData.cos2DeltaLambda +
                              moonData.factor2 * moonData.p22 * moonData.cos2DeltaLambda);
        final double deLsd = -0.25 * lSup1SemiDiurnal * pointData.sin2Phi * pointData.sinPhi *
                             (sunData.factor2  * sunData.p22  * sunData.sin2DeltaLambda +
                              moonData.factor2 * moonData.p22 * moonData.sin2DeltaLambda);

        // combine diurnal and semi-diurnal tides
        final Vector3D outOfPhaseDisplacement = new Vector3D(drOd + drOsd,                pointData.radial,
                                                             dnOd + dnOsd + dnLd + dnLsd, pointData.north,
                                                             deOd + deOsd + deLd + deLsd, pointData.east);

        return inPhaseDisplacement.add(outOfPhaseDisplacement);

    }

    /** Compute the corrections in the frequency domain (step 2 in IERS procedure).
     * @param elements elements affecting Earth orientation
     * @param pointData reference point data
     * @return displacement of the reference point
     */
    private Vector3D frequencyDomainCorrection(final BodiesElements elements, final PointData pointData) {

        // corrections due to diurnal tides
        final double[] cD  = frequencyCorrectionDiurnal.value(elements);
        final double   drD = pointData.sin2Phi * (cD[0] * pointData.cosLambda + cD[1] * pointData.sinLambda);
        final double   dnD = pointData.cos2Phi * (cD[2] * pointData.cosLambda + cD[3] * pointData.sinLambda);
        final double   deD = pointData.sinPhi  * (cD[4] * pointData.cosLambda + cD[5] * pointData.sinLambda);

        // corrections due to zonal long period tides
        final double[] cZ  = frequencyCorrectionZonal.value(elements);
        final double   drZ = (1.5 * pointData.sinPhi2 - 0.5) * cZ[0];
        final double   dnZ = pointData.sin2Phi               * cZ[1];

        return new Vector3D(drD + drZ, pointData.radial,
                            dnD + dnZ, pointData.north,
                            deD,       pointData.east);

    }

    /** Compute the permanent part of the deformation.
     * @param pointData reference point data
     * @return displacement of the reference point
     */
    private Vector3D permanentDeformation(final PointData pointData) {

        final double h2  = hSup0 + hSup2 * pointData.f;
        final double l2  = lSup0 + lSup2 * pointData.f;

        // permanent deformation, which depend only on latitude
        final double factor = FastMath.sqrt(1.25 / FastMath.PI);
        final double dr = factor *       h2 * h0Permanent * pointData.f;
        final double dn = factor * 1.5 * l2 * h0Permanent * pointData.sin2Phi;

        return new Vector3D(dr, pointData.radial,
                            dn, pointData.north);

    }

    /** Holder for various intermediate data related to reference point. */
    private static class PointData {

        /** Reference point position in {@link #getEarthFrame() Earth frame}. */
        private final Vector3D position;

        /** Distance to geocenter. */
        private final double   r;

        /** Sine of geocentric latitude (NOT geodetic latitude). */
        private final double   sinPhi;

        /** Cosine of geocentric latitude (NOT geodetic latitude). */
        private final double   cosPhi;

        /** Square of the sine of the geocentric latitude (NOT geodetic latitude). */
        private final double   sinPhi2;

        /** Square of the cosine of the geocentric latitude (NOT geodetic latitude). */
        private final double   cosPhi2;

        /** Sine of twice the geocentric latitude (NOT geodetic latitude). */
        private final double   sin2Phi;

        /** Cosine of twice the geocentric latitude (NOT geodetic latitude). */
        private final double   cos2Phi;

        /** Sine of longitude. */
        private final double   sinLambda;

        /** Cosine of longitude. */
        private final double   cosLambda;

        /** Unit radial vector (NOT zenith as it starts from geocenter). */
        private final Vector3D radial;

        /** Unit vector in North direction. */
        private final Vector3D north;

        /** Unit vector in East direction. */
        private final Vector3D east;

        /** (3 sin²φ - 1) / 2 where φ is geoCENTRIC latitude of the reference point (NOT geodetic latitude). */
        private final double f;

        /** Simple constructor.
         * @param position reference point position in {@link #getEarthFrame() Earth frame}
         */
        PointData(final Vector3D position) {

            this.position   = position;
            final double x  = position.getX();
            final double y  = position.getY();
            final double z  = position.getZ();
            final double x2 = x * x;
            final double y2 = y * y;
            final double z2 = z * z;

            // preliminary computation related to station position
            final double rho2 = x2 + y2;
            final double rho  = FastMath.sqrt(rho2);
            final double r2   = rho2 + z2;
            r                 = FastMath.sqrt(r2);
            sinPhi            = z / r;
            cosPhi            = rho / r;
            sinPhi2           = sinPhi * sinPhi;
            cosPhi2           = cosPhi * cosPhi;
            sin2Phi           = 2 * sinPhi * cosPhi;
            cos2Phi           = cosPhi2 - sinPhi2;
            if (rho == 0.0) {
                // at pole
                sinLambda = 0.0;
                cosLambda = 1.0;
            } else {
                // regular point
                sinLambda = y / rho;
                cosLambda = x / rho;
            }
            radial = new Vector3D(x / r, y / r, sinPhi);
            north  = new Vector3D(-cosLambda * sinPhi, -sinLambda * sinPhi, cosPhi);
            east   = new Vector3D(-sinLambda, cosLambda, 0);

            // (3 sin²φ - 1) / 2 where φ is geoCENTRIC latitude of the reference point (NOT geodetic latitude)
            f = (z2 - 0.5 * rho2) / r2;

        }

    }

    /** Holder for various intermediate data related to tide generating body. */
    private static class BodyData {

        /** Body position in Earth frame. */
        private final Vector3D position;

        /** Distance to geocenter. */
        private final double r;

        /** Dot product with reference point direction. */
        private final double dot;

        /** Squared dot product with reference point direction. */
        private final double dot2;

        /** Factor for degree 2 tide. */
        private final double factor2;

        /** Factor for degree 3 tide. */
        private final double factor3;

        /** Square of the cosine of the geocentric latitude (NOT geodetic latitude). */
        private final double   cosPhi2;

        /** Sine of twice the geocentric latitude (NOT geodetic latitude). */
        private final double   sin2Phi;

        /** Legendre function P₂¹. */
        private final double p21;

        /** Legendre function P₂². */
        private final double p22;

        /** Sine of the longitude difference with reference point. */
        private final double sinDeltaLambda;

        /** Cosine of the longitude difference with reference point. */
        private final double cosDeltaLambda;

        /** Sine of twice the longitude difference with reference point. */
        private final double sin2DeltaLambda;

        /** Cosine of twice the longitude difference with reference point. */
        private final double cos2DeltaLambda;

        /** Simple constructor.
         * @param position body position in Earth frame
         * @param ratio2 ratio for the degree 2 tide generated by this body
         * @param ratio3 ratio for the degree 3 tide generated by this body
         * @param pointData reference point data
         */
        BodyData(final Vector3D position, final double ratio2, final double ratio3,
                 final PointData pointData) {

            final double x  = position.getX();
            final double y  = position.getY();
            final double z  = position.getZ();
            final double x2 = x * x;
            final double y2 = y * y;
            final double z2 = z * z;

            this.position    = position;
            final double r2  = x2 + y2 + z2;
            r                = FastMath.sqrt(r2);
            dot              = Vector3D.dotProduct(position, pointData.position) / (r * pointData.r);
            dot2             = dot * dot;

            factor2          = ratio2 / (r2 * r);
            factor3          = ratio3 / (r2 * r2);

            final double rho       = FastMath.sqrt(x2 + y2);
            final double sinPhi    = z / r;
            final double cosPhi    = rho / r;
            final double sinCos    = sinPhi * cosPhi;
            cosPhi2                = cosPhi * cosPhi;
            sin2Phi                = 2 * sinCos;
            p21                    = 3 * sinCos;
            p22                    = 3 * cosPhi2;

            final double sinLambda = y / rho;
            final double cosLambda = x / rho;
            sinDeltaLambda  = pointData.sinLambda * cosLambda  - pointData.cosLambda * sinLambda;
            cosDeltaLambda  = pointData.cosLambda * cosLambda  + pointData.sinLambda * sinLambda;
            sin2DeltaLambda = 2 * sinDeltaLambda * cosDeltaLambda;
            cos2DeltaLambda = cosDeltaLambda * cosDeltaLambda - sinDeltaLambda * sinDeltaLambda;

        }

    }

}


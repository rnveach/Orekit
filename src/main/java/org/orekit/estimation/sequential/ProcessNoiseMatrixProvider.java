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
package org.orekit.estimation.sequential;

import org.hipparchus.linear.RealMatrix;
import org.orekit.errors.OrekitException;
import org.orekit.propagation.SpacecraftState;

/** Provider for process noise matrices.
 * @author Luc Maisonobe
 * @since 9.2
 */
public interface ProcessNoiseMatrixProvider {

    /** Get the process noise matrix between previous and current states.
     * <p>
     * The process noise matrix is a covariance matrix corresponding to the
     * parameters managed by the {@link KalmanEstimator Kalman estimator}.
     * The number of rows/columns and their order are as follows:
     * </p>
     * <ul>
     *   <li>The first 6 components correspond to the 6 orbital parameters
     *   of the associated propagator. All 6 parameters must always be present,
     *   regardless of the fact they are estimated or not.</li>
     *   <li>The following components correspond to the subset of propagation
     *   parameters of the associated propagator that are estimated.</li>
     *   <li>The remaining components correspond to the subset of measurements
     *   parameters that are estimated, considering all measurements, even
     *   the ones that correspond to spacecrafts not related to the
     *   associated propagator</li>
     * </ul>
     * <p>
     * In most cases, the process noise for the part corresponding to measurements
     * (the final rows and columns) will be set to 0 for the process noise corresponding
     * to the evolution between a non-null previous and current state. They may
     * be non-null for the initial process noise (i.e. when {@code previous} is null)
     * if the initial matrix is in fact the output matrix of a previous run
     * of the Kalman filter.
     * </p>
     * @param previous previous state, null if initial covariance is desired
     * @param current current state
     * @return physical (i.e. non normalized) process noise matrix between
     * previous and current states
     * @exception OrekitException if matrix cannot be computed
     * @see org.orekit.propagation.conversion.PropagatorBuilder#getOrbitalParametersDrivers()
     * @see org.orekit.propagation.conversion.PropagatorBuilder#getPropagationParametersDrivers()
     */
    RealMatrix getProcessNoiseMatrix(SpacecraftState previous, SpacecraftState current)
        throws OrekitException;

}

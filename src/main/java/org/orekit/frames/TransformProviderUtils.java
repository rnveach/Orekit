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

package org.orekit.frames;

import org.hipparchus.RealFieldElement;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;

/** Utility for Transform providers.
 * @author Luc Maisonobe
 * @since 9.2
 */
public class TransformProviderUtils {

    /** Identity provider.
     * <p>
     * The transforms generated by this providers are always {@link Transform#IDENTITY}.
     * </p>
     */
    public static final TransformProvider IDENTITY_PROVIDER = new TransformProvider() {

        /** Serializable UID. */
        private static final long serialVersionUID = 20180330L;

        /** {@inheritDoc}
         * <p>
         * Always returns {@link Transform#IDENTITY}
         * </p>
         */
        @Override
        public Transform getTransform(final AbsoluteDate date) {
            return Transform.IDENTITY;
        }

        /** {@inheritDoc}
         * <p>
         * Always returns {@link FieldTransform#getIdentity(org.hipparchus.Field)}
         * </p>
         */
        @Override
        public <T extends RealFieldElement<T>> FieldTransform<T> getTransform(final FieldAbsoluteDate<T> date)
            {
            return FieldTransform.getIdentity(date.getField());
        }

    };

    /** Private constructor.
     * <p>This class is a utility class, it should neither have a public
     * nor a default constructor. This private constructor prevents
     * the compiler from generating one automatically.</p>
     */
    private TransformProviderUtils() {
    }

    /** Reverse a transform provider.
     * @param provider provider to reverse
     * @return a new provider which provide a transform similar to
     * {@code provider.getTransform(date).getInverse()}
     */
    public static TransformProvider getReversedProvider(final TransformProvider provider) {
        return new TransformProvider() {

            /** serializable UID. */
            private static final long serialVersionUID = 20180330L;

            /** {@inheritDoc} */
            @Override
            public Transform getTransform(final AbsoluteDate date)
                {
                return provider.getTransform(date).getInverse();
            }

            /** {@inheritDoc} */
            @Override
            public <T extends RealFieldElement<T>> FieldTransform<T> getTransform(final FieldAbsoluteDate<T> date)
                {
                return provider.getTransform(date).getInverse();
            }

        };
    }

    /** Combine two transform providers.
     * @param first first provider to apply
     * @param second second provider to apply
     * @return a new provider which provide a transform similar to
     * {@code new Transform(date, first.getTransform(date), second.getTransform(date))}
     */
    public static TransformProvider getCombinedProvider(final TransformProvider first,
                                                        final TransformProvider second) {
        return new TransformProvider() {

            /** serializable UID. */
            private static final long serialVersionUID = 20180330L;

            /** {@inheritDoc} */
            @Override
            public Transform getTransform(final AbsoluteDate date)
                {
                return new Transform(date, first.getTransform(date), second.getTransform(date));
            }

            /** {@inheritDoc} */
            @Override
            public <T extends RealFieldElement<T>> FieldTransform<T> getTransform(final FieldAbsoluteDate<T> date)
                {
                return new FieldTransform<>(date, first.getTransform(date), second.getTransform(date));
            }

        };
    }

}

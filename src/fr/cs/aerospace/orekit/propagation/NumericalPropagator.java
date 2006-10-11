package fr.cs.aerospace.orekit.propagation;

import java.util.ArrayList;
import java.util.Iterator;


import org.spaceroots.mantissa.ode.ContinuousOutputModel;
import org.spaceroots.mantissa.ode.FirstOrderIntegrator;
import org.spaceroots.mantissa.ode.FirstOrderDifferentialEquations;
import org.spaceroots.mantissa.ode.StepHandler;
import org.spaceroots.mantissa.ode.DummyStepHandler;
import org.spaceroots.mantissa.ode.FixedStepHandler;
import org.spaceroots.mantissa.ode.StepNormalizer;
import org.spaceroots.mantissa.ode.DerivativeException;
import org.spaceroots.mantissa.ode.IntegratorException;
import org.spaceroots.mantissa.ode.SwitchingFunction;
import org.spaceroots.mantissa.utilities.ArrayMapper;
import fr.cs.aerospace.orekit.errors.OrekitException;
import fr.cs.aerospace.orekit.orbits.Orbit;
import fr.cs.aerospace.orekit.orbits.OrbitDerivativesAdder;
import fr.cs.aerospace.orekit.orbits.OrbitalParameters;
import fr.cs.aerospace.orekit.perturbations.ForceModel;
import fr.cs.aerospace.orekit.perturbations.SWF;
import fr.cs.aerospace.orekit.time.AbsoluteDate;
import fr.cs.aerospace.orekit.utils.PVCoordinates;


/**
 * This class propagates an {@link fr.cs.aerospace.orekit.orbits.Orbit Orbit}
 * using numerical integration.
 *
 * <p>The user normally builds an extrapolator by specifying the integrator he
 * wants to use, then adding all the perturbing force models he wants and then
 * performing the given integration with an initial orbit and a target time. The same
 * extrapolator can be reused for several orbit extrapolations.</p>
 
 * <p>Several extrapolation methods are available, providing their results in
 * different ways to better suit user needs.
 * <dl>
 *  <dt>if the user needs only the orbit at the target time</dt>
 *  <dd>he will use {@link #propagate(Orbit,AbsoluteDate,Orbit)}</dd>
 *  <dt>if the user needs random access to the orbit state at any time between
 *      the initial and target times</dt>
 *  <dd>he will use {@link #propagate(Orbit,AbsoluteDate,IntegratedEphemeris)} and
 *  {@link IntegratedEphemeris}</dd>
 *  <dt>if the user needs to do some action at regular time steps during
 *      integration</dt>
 *  <dd>he will use {@link #propagate(Orbit,AbsoluteDate,double,FixedStepHandler)}</dd>
 *  <dt>if the user needs to do some action during integration but do not need
 *      specific time steps</dt>
 *  <dd>he will use {@link #propagate(Orbit,AbsoluteDate,StepHandler)}</dd>
 * </dl></p>
 *
 * <p>The two first methods are used when the user code needs to drive the
 * integration process, whereas the two last methods are used when the
 * integration process needs to drive the user code.
 *
 * @see Orbit
 * @see ForceModel
 * @see StepHandler
 * @see FixedStepHandler
 * @see IntegratedEphemeris
 *
 * @version $Id$
 * @author  M. Romero
 * @author  L. Maisonobe
 * @author  G. Prat
 */
public class NumericalPropagator
  implements FirstOrderDifferentialEquations {

    /** Create a new instance of NumericalExtrapolationModel.
     * After creation, the instance is empty, i.e. there is no perturbing force
     * at all. This means that if {@link #addForceModel addForceModel} is not
     * called after creation, the integrated orbit will follow a keplerian
     * evolution only.
     * @param mu central body gravitational constant (GM).
     * @param integrator numerical integrator to use for extrapolation.
     */
    public NumericalPropagator(double mu, FirstOrderIntegrator integrator) {
      this.mu                 = mu;
      this.forceModels        = new ArrayList();
      this.switchingFunctions = new ArrayList();
      this.integrator         = integrator;
      this.startDate          = new AbsoluteDate();
      this.date               = new AbsoluteDate();
      this.parameters         = null;
      this.mapper             = null;
      this.adder              = null;
    }

    /** Add a force model to the global perturbation model. The associated 
     * switching function is added to the switching functions vector.
     * All models added by this method will be considered during integration.
     * If this method is not called at all, the integrated orbit will follow
     * a keplerian evolution only.
     * @param model perturbing {@link ForceModel} to add
     */
    public void addForceModel(ForceModel model) {
      forceModels.add(model);
    }

    /** Remove all perturbing force models from the global perturbation model, 
     * and associated switching functions.
     * Once all perturbing forces have been removed (and as long as no new force
     * model is added), the integrated orbit will follow a keplerian evolution
     * only.
     */
    public void removeForceModels() {
      forceModels.clear();
      switchingFunctions.clear();
    }

    /** Propagate an orbit up to a specific target date.
     * @param initialOrbit orbit to extrapolate (this object will not be
     * changed except if finalOrbit is also reference to it)
     * @param finalDate target date for the orbit
     * @param finalOrbit placeholder where to put the final orbit (may be a
     * reference to initialOrbit and may be null, as long as null is cast to
     *
     * (Orbit) to avoid ambiguities with the other extrapolation methods)
     * @return orbit at the final date (reference to finalOrbit if it was non
     * null, reference to a new object otherwise)
     * @exception DerivativeException if the force models trigger one
     * @exception IntegratorException if the force models trigger one
     */
    public void propagate(Orbit initialOrbit,
                             AbsoluteDate finalDate, Orbit finalOrbit)
      throws DerivativeException, IntegratorException, OrekitException {

      propagate(initialOrbit, finalDate, DummyStepHandler.getInstance());
      finalOrbit.reset(date, parameters, mu);

    }
    
    /** Propagate an orbit and store the ephemeris throughout the integration
     * range.
     * @param initialOrbit orbit to extrapolate (this object will not be
     * changed)
     * @param finalDate target date for the orbit
     * @param ephemeris placeholder where to put the results
     * @exception DerivativeException if the force models trigger one
     * @exception IntegratorException if the force models trigger one
     */
    public void propagate(Orbit initialOrbit,
                                             AbsoluteDate finalDate,
                                             IntegratedEphemeris ephemeris) 
        throws DerivativeException, IntegratorException, OrekitException {    
    	ContinuousOutputModel model = new ContinuousOutputModel();
    	propagate(initialOrbit, finalDate, (StepHandler)model);
    	ephemeris.initialize(model , initialOrbit.getDate());
    }        

    /** Propagate an orbit and call a user handler at fixed time during
     * integration.
     * @param initialOrbit orbit to extrapolate (this object will not be
     * changed)
     * @param finalDate target date for the orbit
     * @param h fixed stepsize (s)
     * @param handler object to call at fixed time steps
     * @exception DerivativeException if the force models trigger one
     * @exception IntegratorException if the force models trigger one
     */     
    public void propagate(Orbit initialOrbit, AbsoluteDate finalDate,
                            double h, FixedStepHandler handler)
      throws DerivativeException, IntegratorException, OrekitException {
        propagate(initialOrbit, finalDate, new StepNormalizer(h, handler));
    }

    /** Propagate an orbit and call a user handler after each successful step.
     * @param initialOrbit orbit to extrapolate (this object will not be
     * changed)
     * @param finalDate target date for the orbit
     * @param handler object to call at the end of each successful step
     * @exception DerivativeException if the force models trigger one
     * @exception IntegratorException if the force models trigger one
     */    
    public void propagate(Orbit initialOrbit,
                            AbsoluteDate finalDate, StepHandler handler)
      throws DerivativeException, IntegratorException, OrekitException {

        // space dynamics view
        startDate.reset(initialOrbit.getDate());
        date.reset(initialOrbit.getDate());

        // try to avoid building new objects if possible
        if (parameters != null) {
          try {
            parameters.reset(initialOrbit.getParameters(), mu);
          } 
          catch (ClassCastException cce) {
            parameters = null;
          }
        }
        if (parameters == null) {
          parameters = (OrbitalParameters) initialOrbit.getParameters().clone();
          mapper     = new ArrayMapper(parameters);
          adder      = parameters.getDerivativesAdder(mu);
        }

        // mathematical view
        double t0 = 0;
        double t1 = finalDate.minus(startDate);
        mapper.updateArray();
        
        for( int i = 0; i < switchingFunctions.size(); i++) {
          integrator.addSwitchingFunction((MappingSwitchingFunction) switchingFunctions.get(i), 
                                          maxCheckIntervals[i], thresholds[i]);
        }
        
        // mathematical integration
        integrator.setStepHandler(handler);
        integrator.integrate(this, t0, mapper.getInternalDataArray(),
                             t1, mapper.getInternalDataArray());

        // back to space dynamics view
        date.reset(startDate, t1);
        mapper.updateObjects();
        

    }

     public int getDimension() {
      return parameters.getStateDimension();
    }

    /** Computes the orbit time derivative.
     * @param t current time offset from the reference epoch (s)
     * @param y array containing the current value of the orbit state vector
     * @param yDot placeholder array where to put the time derivative of the
     * orbit state vector
     * @exception DerivativeException this exception is propagated to the caller
     * if the underlying user function triggers one
     */
    public void computeDerivatives(double t, double[] y, double[] yDot)
      throws DerivativeException {

      try {
        // update space dynamics view
        mapState(t, y);
        
        // compute cartesian coordinates
        PVCoordinates pvCoordinates = parameters.getPVCoordinates(mu);
        
        // initialize derivatives
        adder.initDerivatives(yDot);
        
        // compute the contributions of all perturbing forces
        for (Iterator iter = forceModels.iterator(); iter.hasNext();) {
            ((ForceModel) iter.next()).addContribution(date, pvCoordinates, adder);
        }
        
        // finalize derivatives by adding the Kepler contribution
        adder.addKeplerContribution();
      } catch (OrekitException oe) {
        throw new DerivativeException(oe.getMessage(), new String[0]);
      }
        
    }

    private void mapState(double t, double [] y) {

      // update space dynamics view
      date.reset(startDate, t);
      mapper.updateObjects(y);

      parameters.mapStateFromArray(0,y);

    }

    private class MappingSwitchingFunction implements SwitchingFunction {
      
      public MappingSwitchingFunction(SWF swf) {
          this.swf = swf;
      }
      
      public double g(double t, double[] y){
          mapState(t, y);
          try {
            return swf.g(date, parameters.getPVCoordinates(mu), parameters.getFrame());
          } catch (OrekitException oe) {
            // TODO provide the exception to the surrounding NumericalPropagator instance
            throw new RuntimeException("... TODO ...");
          }
      }
      
      public int eventOccurred(double t, double[] y) {
          mapState(t, y);
          swf.eventOccurred(date, parameters.getPVCoordinates(mu), parameters.getFrame());
          return CONTINUE;
      }
      
      public void resetState(double t, double[] y) {
      }
      
      private SWF swf;
      
    }
  
    /** Central body gravitational constant. */
    private double mu;
    
    /** Force models used during the extrapolation of the Orbit. */
    private ArrayList forceModels;
    
    /** Switching functions used during the extrapolation of the Orbit. */
    private ArrayList switchingFunctions;

    /** threshold associated to switching functions. */
    private double[] thresholds;
    
    /** Maximal time intervals between switching function checks. */
    private double[] maxCheckIntervals;
    
    /** Start date. */
    private AbsoluteDate startDate;

    /** Current date. */
    private AbsoluteDate date;

    /** Current orbital parameters, updated during the integration process. */
    private OrbitalParameters parameters;
        
    /** Mapper between the orbit domain object and flat state array. */
    private ArrayMapper mapper;
    
    /** Integrator selected by the user for the orbital extrapolation process. */
    private FirstOrderIntegrator integrator;

    /** Gauss equations handler. */
    private OrbitDerivativesAdder adder;

}

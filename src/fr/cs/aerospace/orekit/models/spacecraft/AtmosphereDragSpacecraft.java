package fr.cs.aerospace.orekit.models.spacecraft;

import org.spaceroots.mantissa.geometry.Vector3D;
import fr.cs.aerospace.orekit.forces.perturbations.Drag;

/** Adapted container for the Atmosphere drag force model.
 * 
 * @see Drag
 * @author F. Maussion
 */
public interface AtmosphereDragSpacecraft {

  /** Get the surface.
   * @param direction direction of the flux in the spacecraft frame
   * @return surface (m<sup>2</sup>)
   */
  public double getSurface(Vector3D direction);

  /** Get the drag coefficients vector.
   * @param direction direction of the flux in the spacecraft frame
   * @return drag coefficients vector (direction in the spacecraft frame)
   */
  public Vector3D getDragCoef(Vector3D direction);

}

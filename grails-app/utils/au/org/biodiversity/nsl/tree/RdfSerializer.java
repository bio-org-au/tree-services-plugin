package au.org.biodiversity.nsl.tree;

import java.io.Writer;

/**
 * An object that knows how to write an RdfRenderable into an output stream.
 * 
 * @author ibis
 *
 */

public interface RdfSerializer {
	/** Write the RDF */
	public void render(Writer w);
}

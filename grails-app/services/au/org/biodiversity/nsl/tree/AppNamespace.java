package au.org.biodiversity.nsl.tree;


/**
 * Certain rdf namespaces are used internally by this grails app and have a standard label component
 * 
 * TODO: do I actually need this enum? At all?
 * @author ibis
 *
 */

public enum AppNamespace {
	/** root for the namespace vocabulary items */
	voc,
	/** namespace for the namespace URIs. */
	ns, 
	/** namespace for classification (tree) labels */
	clsf,
	/** namespace for arrangements */
	arr,
	/** namespace for nodes */
	node;
}

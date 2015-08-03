/*
    Copyright 2015 Australian National Botanic Gardens

    This file is part of NSL tree services plugin project.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy
    of the License at http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

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

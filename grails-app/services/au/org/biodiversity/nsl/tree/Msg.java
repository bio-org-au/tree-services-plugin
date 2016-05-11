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
 * A list of resource bundle keys used for ServiceException messages.
 * @author ibis
 *
 */

public enum Msg {
	NO_MESSAGE,
	EMPTY,
	
	// BasicOperationsService
	
	createDraftNode, 
	updateDraftNode,
	deleteDraftNode,
	updateDraftNodeLink,
	updateDraftNodeLink2,
	deleteDraftNodeLink,
	deleteDraftNodeLink2,
	deleteDraftTree,
    deleteArrangement,
	persistNode,
	persistAll,
	adoptNode,
	checkoutNode,
	checkoutLink,
	undoCheckout,
	performVersioning,
	findNodeCurrentOrCheckedout,
	
	PERSISTENT_NODE_NOT_PERMITTED,
	DRAFT_NODE_NOT_PERMITTED,
	ROOT_NODE_NOT_PERMITTED,
	OLD_NODE_NOT_PERMITTED,
	END_NODE_NOT_PERMITTED,
	NODE_WITH_SUBNODES_NOT_PERMITTED,
	NO_LINK_SEQ_N,
	NEW_SUPERNODE_BELONGS_TO_DIFFERENT_DRAFT_TREE,
	NODE_HAS_NO_PREV,
	NODE_HAS_MORE_THAN_ONE_SUPERNODE,
	NODE_APPEARS_IN_MULTIPLE_LOCATIONS_IN,
	NODES_ARE_USED_IN_OTHER_TREES,
	LOOP_DETECTED,
	DRAFT_NODE_HAS_A_DRAFT_SUBNODE_IN_ANOTHER_ARRANGEMENT,
    CANNOT_MIX_LITERAL_AND_URI,
    NAME_URI_DOESNT_MATCH,
    INSTANCE_URI_DOESNT_MATCH,
	NAMESPACE_MISMATCH,
	CHECKOUT_MUST_APPEAR_ONCE,

	// profile data messages
	LITERAL_NODE_MAY_NOT_HAVE_SUBNODES,
	CANNOT_CREATE_SYSTEM_NODES,
	CANNOT_CREATE_TEMP_NODES,
	CANNOT_CREATE_DOCUMENT_NODE_WITH_A_NAME_OR_TAXON,
	CANNOT_CREATE_VALUE_NODE_WITH_A_NAME_OR_TAXON,
    CANNOT_CREATE_TAXONOMIC_NODE_WITH_A_LITERAL_VALUE,
    VALUE_NODES_MUST_HAVE_RESOURCE_OR_LITERAL,
	VALUE_NODES_MUST_USED_FIXED_LINK,
	
	CANNOT_UPDATE_PROFILE_ITEM,
	MULTIPLE_PROFILE_VALUES_FOUND,
	
	// TreeOperationsService
	THING_NOT_FOUND_IN_ARRANGEMENT,
	THING_FOUND_IN_ARRANGEMENT_MULTIPLE_TIMES,
	THING_FOUND_IN_ARRANGEMENT,

	// ClassificationManagagerService
	createClassification,
	updateClassification,
	deleteClessification,

	LABEL_ALREADY_EXISTS,

	// VersioningService

	CANNOT_REPLACE_NODE,
	CANNOT_USE_NODE_AS_A_REPLACEMENT,
	CANNOT_REPLACE_NODE_WITH_NODE_BEING_REPLACED,
	CANNOT_REPLACE_NODE_WITH_NODE_BEING_SYNTHETICALLY_REPLACED,
	CANNOT_REPLACE_NODE_WITH_NODE_ABOVE_A_TRACKING_LINK_ETC,
	NODE_WOULD_BE_ORPHANED;
	
	public String getKey() { return "nsl2.boatree.service_exception." + name(); }
}

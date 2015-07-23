class TreeServicesUrlMappings {

    static mappings = {

        group "/classification/", {
            "$label/form"(controller: 'treeOperations', action: 'index')

            "$label/has-name/"(controller: 'treeOperations', action: 'hasName')
            "$label/has-name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'hasName')

            "$label/has-taxon/"(controller: 'treeOperations', action: 'hasTaxon')
            "$label/has-taxon/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'hasTaxon')

            "$label/nodes-by-name"(controller: 'treeOperations', action: 'findName')
            "$label/nodes-by-taxon"(controller: 'treeOperations', action: 'findTaxon')

            "$label/nodes-by-name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'findName') {
                idsOnly = false
                multiValues = true
            }

            "$label/nodes-by-taxon/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'findTaxon') {
                idsOnly = false
                multiValues = true
            }

            "$label/brief-node-by-name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'findName') {
                idsOnly = true
                multiValues = false
            }

            "$label/brief-node-by-taxon/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'findTaxon') {
                idsOnly = true
                multiValues = false
            }

            "$label/brief-path-by-taxon/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'pathByTaxon') {
                idsOnly = true
                multiValues = false
            }

            "$label/brief-path-by-name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'pathByName') {
                idsOnly = true
                multiValues = false
            }


            "$label/name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'findName', method: "GET") {
                idsOnly = false
                multiValues = true
            }

            "$label/name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'addName', method: "PUT")
            "$label/name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'updateName', method: "POST")
            "$label/name/$nsPart:$idPart(.$format)?"(controller: 'treeOperations', action: 'deleteName', method: "DELETE")

            "$label/crud"(controller: 'treeOperations', action: 'crud')
            "$label/fixup"(controller: 'treeOperations', action: 'fixup')
            "$label"(controller: 'getData', action: 'contentNegotiationRedirect')
            "$label.$format"(controller: 'getData', action: 'getArrangementByLabel')
        }

        group "/arrangement/", {
            "$id"(controller: 'getData', action: 'contentNegotiationRedirect')
            "$id.$format"(controller: 'getData', action: 'getArrangementById')

        }

        group "/node/", {
            "$id"(controller: 'getData', action: 'contentNegotiationRedirect')
            "$id.$format"(controller: 'getData', action: 'getNodeById')
        }

        group "/voc/", {
            "$vocItem"(controller: 'vocabulary', action: 'contentNegotiationRedirect')
            "$vocItem.$format"(controller: 'vocabulary', action: 'getVoc')
        }
    }
}

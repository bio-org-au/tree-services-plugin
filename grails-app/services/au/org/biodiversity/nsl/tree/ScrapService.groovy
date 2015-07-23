package au.org.biodiversity.nsl.tree

import grails.transaction.Transactional
import org.hibernate.SessionFactory

import au.org.biodiversity.nsl.*

@Transactional( rollbackFor=[ServiceException] )

class ScrapService {
	static datasource = 'nsl'

	SessionFactory sessionFactory_nsl
	
	BasicOperationsService basicOperationsService
	VersioningService versioningService

	Event createEventWithoutRaisingException() {
		Event e = basicOperationsService.newEvent('createEventWithoutRaisingException')
		e.save()
	}

	void createEventWithRuntimeException() {
		Event e = basicOperationsService.newEvent('createEventWithRuntimeException')
		e.save()
		throw new RuntimeException('createEventWithRuntimeException')
	}

	void createEventWithServiceException() {
		Event e = basicOperationsService.newEvent('createEventWithServiceException')
		e.save()
		ServiceException.raise ServiceException.makeMsg(Msg.NO_MESSAGE)
	}

	void createEventWithSubclassException() {
		Event e = basicOperationsService.newEvent('createEventWithSubclassException')
		e.save()
		throw new SubclassException()
	}

	void createEventWithOtherException() {
		Event e = basicOperationsService.newEvent('createEventWithOtherException')
		e.save()
		throw new OtherException()
	}

	class SubclassException extends ServiceException {
		SubclassException() {
			super(makeMsg(Msg.NO_MESSAGE))
		}
	}

	class OtherException extends Exception {
	}

	void raiseException() {
		ServiceException.raise ServiceException.makeMsg(Msg.NO_MESSAGE)
	}


	Arrangement createScrapTree() {
		Arrangement r = new Arrangement()
		r.arrangementType = ArrangementType.Z
		r.synthetic = 'N'
		r.save()
		return r
	}

	def createClassification() {
		Arrangement tmp = Arrangement.findByLabel('TMP')
		if(tmp) {
			basicOperationsService.deleteArrangement(tmp)
		}

		Event e = basicOperationsService.newEvent('create TMP classification')

		tmp = basicOperationsService.createClassification(e, 'TMP', "TMP ${new java.util.Date()}")

		Arrangement tempSpace = basicOperationsService.createTemporaryArrangement()

		Link lk = basicOperationsService.adoptNode(tempSpace.node, DomainUtils.getSingleSubnode(tmp.node), VersioningMethod.F)

		basicOperationsService.checkoutLink(lk)

		lk.refresh()

		for(int l0 = 1; l0<=3;l0++) {
			Node n0 = basicOperationsService.createDraftNode(lk.subnode,
			VersioningMethod.V, NodeInternalType.T, 
			name: UriNs.ns('nsl-name').u("n${l0}"),
			taxon: UriNs.ns('nsl-instance').u("t${l0}"))

			for(int l1 = 1; l1<=l0;l1++) {
				Node n1 = basicOperationsService.createDraftNode(n0,
				VersioningMethod.V, NodeInternalType.T, 
				name: UriNs.ns('nsl-name').u("n${l0}${l1}"),
				taxon: UriNs.ns('nsl-instance').u("t${l0}${l1}"))

				for(int l2 = 1; l2<=l1;l2++) {
					Node n2 = basicOperationsService.createDraftNode(n1,
					VersioningMethod.V, NodeInternalType.T, 
					name: UriNs.ns('nsl-name').u("n${l0}${l1}${l2}"),
					taxon: UriNs.ns('nsl-instance').u("t${l0}${l1}${l2}"))
				}
			}
		}

		basicOperationsService.persistNode e, lk.subnode
		Map<Node, Node> v = new HashMap<Node, Node>()
		v.put(DomainUtils.getSingleSubnode(tmp.node), lk.subnode)

		versioningService.performVersioning e, v, tmp

		basicOperationsService.moveFinalNodesFromTreeToTree tempSpace, tmp
		basicOperationsService.deleteArrangement tempSpace
	}
	
	def experiment() {
		return "this is experiment() in scrapservice";
	}
}

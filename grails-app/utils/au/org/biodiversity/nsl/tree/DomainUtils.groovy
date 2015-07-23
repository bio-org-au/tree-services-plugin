package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.*

class DomainUtils {
	// methods relating to the UriNs class

	static UriNs ns(String ns) {
		return UriNs.findByLabel(ns)
	}

	static UriNs getBlankNs() {
		return UriNs.get(0)
	}

	static Uri getStandaloneUri(String s) {
		return u(getBlankNs(),s)
	}

	static UriNs getBoatreeNs() {
		return UriNs.get(1)
	}

	static Uri getBoatreeUri(String s) {
		return u(getBoatreeNs(),s)
	}

	static UriNs getVocNs() {
		return ns('voc')
	}

	/** get the namespace that this instance of this app uses as the root for the namespace vocabulary items */
	static UriNs getNamespaceNs() {
		return ns('ns')
	}

	/** get the namespace that this instance of this app uses as the root for the tree items */
	static UriNs getTreeNs() {
		return ns('clsf')
	}

	/** get the namespace that this instance of this app uses as the root for the node items */
	static UriNs getRootNs() {
		return ns('arr')
	}

	/** get the namespace that this instance of this app uses as the root for the node items */
	static UriNs getNodeNs() {
		return ns('node')
	}

	static boolean isBlank(UriNs ns) {
		return ns.id == 0
	}

	static boolean isBoatree(UriNs ns) {
		return ns.id == 1
	}

	static boolean hasOwnerUri(UriNs ns) {
		return !!getOwnerUri(ns)
	}

	static Uri getOwnerUri(UriNs ns) {
		if(ns == null) return null;
		return new Uri(ns.ownerUriNsPart, ns.ownerUriIdPart)
	}

	static Uri getNamespaceUri(UriNs ns) {
		if(ns == null) return null;
		if(isBlank(ns)) {
			return new Uri(null, null)
		}
		else if(isBoatree(ns)) {
			// URIs in the voc namespace belong to the vocavbulary at BOA
			return new Uri(DomainUtils.getBoatreeNs(), 'voc')
		}
		else {
			// other namespaces are served up from here.
			return new Uri(DomainUtils.getNamespaceNs(ns), ns.label)
		}
	}

	static Uri uri(String nsPart, Object idPart) {
		return u(ns(nsPart), idPart);
	}

	static Uri u(UriNs ns, Object idPart) {
		if(ns == null) return null;
		return new Uri(ns, idPart == null ? null : idPart.toString())
	}

	// methods relating to the Arranagement class

	static Uri getArrangementTypeUri(ArrangementType t) {
		if(t == null) return null;
		return DomainUtils.getBoatreeUri(t.uriId);
	}



	static Arrangement getEndTree() {
		return Arrangement.get(0)
	}

	static boolean isEndTree(Arrangement a) {
		return a.id == 0
	}

	static Uri getArrangementUri(Arrangement a) {
		if(a == null) return null;
		if(isEndTree(a)) {
			return new Uri(getBoatreeNs(), 'END-TREE')
		}
		else {
			return new Uri(getRootNs(), a.id)
		}
	}

	static Uri getTreeUri(Arrangement a) {
		if(a == null) return null;
		if(isEndTree(a)) {
			return new Uri(getBoatreeNs(), 'END-TREE')
		}
		else if (!a.label) {
			throw new IllegalStateException("Arrangement ${this} is not a tree.")
		}
		else {
			return new Uri(getTreeNs(), a.label)
		}
	}

	static Uri getSafeUri(Arrangement a) {
		if(a == null) return null;
		return a.arrangementType.isTree() ? getTreeUri(a) : getArrangementUri(a)
	}

	// Methods relating to the NODE class

	/**
	 * Get the global end tree. There is only one instance of this tree across all instances of this
	 * schema.
	 * @return
	 */

	static Node getEndNode() {
		return Node.get(0)
	}

	static boolean isEndNode(Node n) {
		return n.id == 0
	}

	static Uri getNodeUri(Node n) {
		if(n == null) return null;
		if(isEndNode(n)) {
			return new Uri(getBoatreeNs(), 'END-NODE')
		}
		else {
			return new Uri(getNodeNs(), n.id)
		}
	}

	static Uri getNodeTypeUri(Node n) {
		if(n == null) return null;
		return getRawNodeTypeUri(n) ?: getDefaultNodeTypeUriFor(n.internalType)
	}

	static Uri getRawNodeTypeUri(Node n) {
		if(n == null) return null;
		return u(n.typeUriNsPart, n.typeUriIdPart)
	}


	static boolean isBlankUri(UriNs nsPart, String idPart) {
		return (nsPart == null || isBlank(nsPart)) && !idPart
	}

	static Uri getDefaultNodeTypeUri() {
		return getDefaultNodeTypeUriFor(NodeInternalType.T)
	}

	static boolean hasName(Node n) {
		return !isBlankUri(n.nameUriNsPart, n.nameUriIdPart)
	}

	static Uri getNameUri(Node n) {
		if(n == null) return null;
		return hasName(n) ? new Uri(n.nameUriNsPart, n.nameUriIdPart) : null
	}

	static boolean hasTaxon(Node n) {
		return !isBlankUri(n.taxonUriNsPart, n.taxonUriIdPart)
	}

	static Uri getTaxonUri(Node n) {
		if(n == null) return null;
		return hasTaxon(n) ?  new Uri(n.taxonUriNsPart, n.taxonUriIdPart) : null
	}

	static boolean hasResource(Node n) {
		return !isBlankUri(n.resourceUriNsPart, n.resourceUriIdPart)
	}

	static Uri getResourceUri(Node n) {
		if(n == null) return null;
		return hasResource(n) ? new Uri(n.resourceUriNsPart, n.resourceUriIdPart) : null
	}

	static boolean isCheckedIn(Node n) {
		return n.checkedInAt != null
	}

	static boolean isReplaced(Node n) {
		return n.replacedAt != null
	}

	static boolean isCurrent(Node n) {
		return isCheckedIn(n) && !isReplaced(n)
	}

	/* this is a mess - hasSingleSuperlink does not check the same condition as getSingleSuperlink
	 * one method throws an exception id there is more than one, the other does not.
	 * The underlying issue is that different services need different things. Thise fine little distinctions ought not to be here.
	 */

	static Link getDraftNodeSuperlink(Node n) {
		if(n == null) return null;
		if(isCheckedIn(n)) throw new IllegalStateException()
		if(n.supLink.size() == 0) return null
		if(n.supLink.size() > 1) throw new IllegalStateException()
		return n.supLink.iterator().next()
	}

	static Node getDraftNodeSupernode(Node n) {
		return getDraftNodeSuperlink(n)?.supernode
	}

	/**
	 * @return the superlink, or null if there is no supernode or more than one.
	 */

	static Link getSingleCurrentSuperlinkInTree(Node n) {
		if(n == null) return null;
		Link l = null

		for(Link ll: n.supLink) {
			if(!isCurrent(ll.supernode) || ll.supernode.root != n.root) continue
				if(l) return null
			l = ll
		}
		return l
	}



	static boolean hasSingleSuperlink(Node n) {
		return n.supLink.size()==1
	}

	static boolean hasSingleSupernode(Node n) {
		return n.supLink.size()==1
	}

	static Link getSingleSuperlink(Node n) {
		if(n == null) return null;
		Link sup = null
		n.supLink.each {
			if(isCurrent(it.supernode)) {
				if(sup) {
					throw new IllegalStateException("multiple current superlinks on ${this}")
				}
				else {
					sup = it
				}
			}
		}
		return sup
	}

	static Link getSingleSublink(Node n) {
		if(n == null) return null;
		if(n.subLink.size()==0) return null
		if(n.subLink.size()==1) return n.subLink.iterator().next()
		throw new IllegalStateException("${n.subLink.size()} sublinks")
	}

	static Node getSingleSupernode(Node n) {
		if(n == null) return null;
		return getSingleSuperlink(n)?.supernode
	}

	static Node getSingleSubnode(Node n) {
		if(n == null) return null;
		return getSingleSublink(n)?.subnode
	}

	// note that element zero is always null.

	static Link[] getSublinksAsArray(Node n) {
		if(n == null) return null;
		int maxIdx = 0
		for(Link l: n.subLink) {
			if(l.linkSeq > maxIdx) {
				maxIdx = l.linkSeq
			}
		}

		Link[] v = new Link[maxIdx+1]
		for(Link l: n.subLink) {
			assert v[l.linkSeq] == null
			v[l.linkSeq] = l
		}
		return v
	}

	static Collection<Link> getSubtaxaAsList(Node n) {
		if(n == null) return null;
		return n.subLink.findAll { it.subnode.internalType.isTree()}
	}

	static Collection<Link> getProfileItemsAsList(Node n) {
		if(n == null) return null;
		return n.subLink.findAll { it.subnode.internalType.isProfile()}
	}

	static Map<Uri, Link> getProfileItemsAsMap(Node n) {
		if(n == null) return null;
		Map<Uri, Link> mm = new HashMap<Uri, Link>();
		getProfileItemsAsList(n).each { mm.put(DomainUtils.getLinkTypeUri(it), it)}
		return mm
	}

	static Link getProfileItemByString(Node n, String propertyNsPart, String propertyIdPart) {
		if(n == null) return null;
		return getProfileItem(n, uri(propertyNsPart, propertyIdPart))
	}

	static Link getProfileItem(Node n, Uri property) {
		if(n == null) return null;
		Collection<Link> pp = n.subLink.findAll {Link it -> getLinkTypeUri(it) == property}
		return pp.isEmpty() ? null : pp.first()
	}

	// methods relating to the LINK class

	static Uri getLinkTypeUri(Link l) {
		if(l == null) return null;
		return getRawLinkTypeUri(l) ?: getDefaultLinkTypeUriFor(l.subnode.internalType);
	}

	static Uri getRawLinkTypeUri(Link l) {
		if(l == null) return null;
		return DomainUtils.u(l.typeUriNsPart, l.typeUriIdPart)
	}

	static getDefaultLinkTypeUri() {
		return getDefaultLinkTypeUriFor(NodeInternalType.T)
	}

	static Uri getDefaultNodeTypeUriFor(NodeInternalType t) {
		switch(t) {
			case NodeInternalType.S: return getBoatreeUri('system-node');
			case NodeInternalType.Z: return getBoatreeUri('temp-node');
			case NodeInternalType.T: return getBoatreeUri('placement');
			case NodeInternalType.D: return getBoatreeUri('document');
			case NodeInternalType.V: return uri('xs','string');
		}
	}

	static Uri getDefaultLinkTypeUriFor(NodeInternalType t) {
		switch(t) {
			case NodeInternalType.S: return getBoatreeUri('system-link');
			case NodeInternalType.Z: return getBoatreeUri('temp-link');
			case NodeInternalType.T: return getBoatreeUri('subnode');
			case NodeInternalType.D: return getBoatreeUri('hasValue');
			case NodeInternalType.V: return getBoatreeUri('hasValue');
		}
	}


	static void setLinkTypeUri(Link l, Uri typeUri) {
		if(l != null) {
            if (typeUri) {
                l.typeUriNsPart = typeUri.nsPart
                l.typeUriIdPart = typeUri.idPart
            } else {
                l.typeUriNsPart = getBlankNs()
                l.typeUriIdPart = null
            }
        }
	}

	// Methods supporting our basic operations service protocol. After a call to basic operations service,
	// the hibernate session is flushed and objects must be refetched.

	static Node refetchNode(Node o) {
		return o == null ? null : Node.get(o.id);
	}

	static Link refetchLink(Link o) {
		return o == null ? null : Link.get(o.id);
	}

	static Arrangement refetchArrangement(Arrangement o) {
		return o == null ? null : Arrangement.get(o.id);
	}

	static Event refetchEvent(Event o) {
		return o == null ? null : Event.get(o.id);
	}

	static UriNs refetchUriNs(UriNs o) {
		return o == null ? null : UriNs.get(o.id);
	}

	static Uri refetchUri(Uri o) {
		return o == null ? null : new Uri(UriNs.get(o.nsPart.id), o.idPart);
	}

    static Name refetchName(Name o) {
        return o == null ? null : Name.get(o.id);
    }

    static Instance refetchInstance(Instance o) {
        return o == null ? null : Instance.get(o.id);
    }

    static <K, V> Map<K, V> refetchMap(Map<K, V> o) {
		if(o==null) return null;
		Map<K, V> oo = [:];
		o.each { k, v -> oo[refetchObject(k)] = refetchObject(v)}
		return oo;
	}

	static <T> Collection<T> refetchCollection(Collection<T> o) {
		if(o==null) return o;
		return o.collect { refetchObject(it) }
	}

	static <T> T refetchObject(T o) {
		if(o instanceof Node) {
			return refetchNode(o as Node) as T;
		}
		else if(o instanceof Link) {
			return refetchLink(o as Link) as T;
		}
		else if(o instanceof Arrangement) {
			return refetchArrangement(o as Arrangement) as T;
		}
		else if(o instanceof Event) {
			return refetchEvent(o as Event) as T;
		}
		else if(o instanceof UriNs) {
			return refetchUriNs(o as UriNs) as T;
		}
        else if(o instanceof Uri) {
            return refetchUri(o as Uri) as T;
        }
        else if(o instanceof Name) {
            return refetchName(o as Name) as T;
        }
        else if(o instanceof Instance) {
            return refetchInstance(o as Instance) as T;
        }
		else if(o instanceof Map) {
			return refetchMap(o as Map) as T;
		}
		else if (o instanceof Collection) {
			return refetchCollection(o as Collection) as T;
		}
		else {
			return o;
		}
	}
}
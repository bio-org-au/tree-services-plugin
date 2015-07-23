package au.org.biodiversity.nsl.tree

import groovy.xml.MarkupBuilder

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import au.org.biodiversity.nsl.UriNs;

/**
 * Serialize an RdfRenderable as RDF.
 * @author ibis
 * @see http://www.w3.org/TR/REC-rdf-syntax/
 *
 */

/*
 * I am not at all convinced that the pattern I am using is the right way to go. It seems that MarkupBuilder is mainly
 * useful for building XML when you need to produce static lumps of XML with perhaps a few values substituted in, and 
 * want to use a format that looks ok in code. That isn't what is being done here. 
 */

class RdfAsXml implements RdfSerializer {
	static final Log log = LogFactory.getLog(RdfAsXml.class)

	final RdfRenderable.Top r

	RdfAsXml(RdfRenderable.Top r) {
		if(!r) throw new IllegalArgumentException('null')
		this.r = r
	}

	static String asTag(Uri u) {
		if(!u) return "NULL"
		if(DomainUtils.isBlank(u.nsPart) || DomainUtils.isBoatree(u.nsPart)) {
			return u.idPart
		}
		else {
			return u.asQName()
		}
	}

	public void render(Writer writer) {
		MarkupBuilder xml = new MarkupBuilder(writer)
		xml.mkp.xmlDeclaration(version:'1.0')


		Map<String, String> nsmap = new HashMap<String, String>()
		nsmap.put('xmlns', UriNs.getBoatreeNs().uri)

		RdfRenderable.Info info = new RdfRenderable.Info()
		r.getInfo(info)
		for(UriNs ns: info.namespaces) {
			if(DomainUtils.isBlank(ns) || DomainUtils.isBoatree(ns)) continue
				nsmap.put("xmlns:${ns.label}", ns.uri)
		}


		Closure renderRenderable = {
			Collection<RdfRenderable.Obj> todo = new ArrayList<RdfRenderable.Obj> ()
			if(r instanceof RdfRenderable.Obj) {
				RdfAsXml.renderAsTopLevelItem(xml, todo, (RdfRenderable.Obj)r)
			}
			else {
				((RdfRenderable.Coll)r).each {
					if(it) {
						RdfAsXml.renderAsTopLevelItem(xml, todo, (RdfRenderable.Obj)it)
					}
				}
			}

			while(!todo.isEmpty()) {
				Collection<RdfRenderable.Obj> todoNext = new ArrayList<RdfRenderable.Obj> ()
				todo.each {
					RdfAsXml.renderAsTopLevelItem(xml, todoNext, (RdfRenderable.Obj)it)
				}
				todo = todoNext
			}
		}

		xml.'rdf:RDF' (nsmap) { renderRenderable() }
	}

	static void renderAsTopLevelItem(MarkupBuilder xml, Collection<RdfRenderable.Obj> todo, RdfRenderable o) {
		if(o instanceof RdfRenderable.Obj) {
			Closure renderProps = {
				renderObjProps(xml, todo, (RdfRenderable.Obj) o)
			}
			if(((RdfRenderable.Obj)o).getId()) {
				xml."${asTag(o.getTypeUri())}"([ 'rdf:about' : ((RdfRenderable.Obj)o).getId().asUri()]) { renderProps() }
			}
			else {
				xml."${asTag(o.getTypeUri())}"() { renderProps() }
			}
		}
		else
		if(o instanceof RdfRenderable.Coll) {
			Closure renderElements = {
				((RdfRenderable.Coll)o).each {
					renderAsTopLevelItem(xml, todo, it)
				}
			}
			xml."rdf:Description"([ 'rdf:ParseType' : o instanceof RdfRenderable.Bag ? 'Bag' : 'Seq']) { renderElements() }
		}
		else
		if(o instanceof RdfRenderable.Primitive) {
			RdfRenderable.Primitive p = (RdfRenderable.Primitive) o
			xml.'rdf:Description'(['rdf:datatype':rdfTypeOf(p)],p.o)
		}
		else
		if(o instanceof RdfRenderable.Resource) {
			RdfRenderable.Resource r = (RdfRenderable.Resource) o
			xml.'rdf:Description'(['rdf:about':r.uri.asUri()])
		}
		else throw new IllegalStateException()
	}

	static void renderObjProps(MarkupBuilder xml, Collection<RdfRenderable.Obj> todo, RdfRenderable.Obj o) {
		o.each { Uri k, RdfRenderable.Obj.Value vv ->
			if(vv.isSingleValue()) {
				renderObjProp(xml, todo, k, vv.asSingleValue().v)
			}
			else {
				vv.asMultiValue().each {  RdfRenderable it ->
					renderObjProp(xml, todo, k, it)
				}
			}
		}
	}

	static void renderObjProp(MarkupBuilder xml, Collection<RdfRenderable.Obj> todo, Uri k, RdfRenderable v) {
		if(v instanceof RdfRenderable.Resource) {
			xml."${asTag(k)}"(['rdf:resource':((RdfRenderable.Resource)v).uri.asUri()]) {
			}
		}
		else
		if(v instanceof RdfRenderable.Primitive) {
			RdfRenderable.Primitive p = (RdfRenderable.Primitive) v
			xml."${asTag(k)}"(['rdf:datatype':rdfTypeOf(p)],p.o)
		}
		else
		if(v instanceof RdfRenderable.Obj) {
			RdfRenderable.Obj oo = (RdfRenderable.Obj) v

			if(oo.getId()) {
				todo.add oo
				xml."${asTag(k)}"(['rdf:resource':oo.getId().asUri()]) {
				}
			}
			else {
				Closure renderProps = {
					renderObjProps(xml, todo, oo)
				}
				xml."${asTag(k)}"(['rdf:parseType':'Resource']) { renderProps() }
			}
		}
		else
		if(v instanceof RdfRenderable.Coll) {

			RdfRenderable.Coll bag = (RdfRenderable.Coll) v

			if(v instanceof RdfRenderable.Seq) {
				Closure renderCollection = {
					for(int i = 1; i<bag.size(); i++) {
						RdfRenderable seqItem = bag.get(i)
						if(seqItem != null) {
							if(seqItem instanceof RdfRenderable.Primitive) {
								xml.'rdf:Description'(['rdf:value': ((RdfRenderable.Primitive)seqItem).o?.toString()]) {}
							}
							else if(seqItem instanceof RdfRenderable.Resource) {
								xml.'rdf:Description'(['rdf:resource': ((RdfRenderable.Resource)seqItem).uri.asUri()]) {}
							}
							else {
								renderAsTopLevelItem(xml, todo, (RdfRenderable.Obj) seqItem)
							}
						}
						else {
							xml.'rdf:Description'()
						}
					}
				}
				xml."${asTag(k)}"(['rdf:parseType':'Seq']) { renderCollection() }
			}
			else
			if(v instanceof RdfRenderable.Bag) {
				Closure renderCollection = {
					for(int i = 0; i<bag.size(); i++) {
						RdfRenderable seqItem = bag.get(i)
						if(seqItem) {
							renderAsTopLevelItem(xml, todo, (RdfRenderable.Obj) seqItem)
						}
					}
				}
				xml."${asTag(k)}"(['rdf:parseType':'Bag']) { renderCollection() }
			}
			else {
				Closure renderCollection = {
					for(int i = 0; i<bag.size(); i++) {
						RdfRenderable seqItem = bag.get(i)
						if(seqItem) {
							renderAsTopLevelItem(xml, todo, (RdfRenderable.Obj) seqItem)
						}
						else {
							xml.'rdf:Description'()
						}
					}
				}
				xml."${asTag(k)}"(['rdf:parseType':'Collection']) { renderCollection() }
			}
		}
		else {
			throw new IllegalStateException()
		}
	}

	static String rdfTypeOf(RdfRenderable.Primitive p) {
		Object o = p.o

		if(o instanceof String) return "http://www.w3.org/2001/XMLSchema#string"

		if(o instanceof Boolean) return "http://www.w3.org/2001/XMLSchema#boolean"

		if(o instanceof Character) return "http://www.w3.org/2001/XMLSchema#string"

		if(o instanceof Number) {
			if(o instanceof Byte) return "http://www.w3.org/2001/XMLSchema#integer"
			if(o instanceof Short) return "http://www.w3.org/2001/XMLSchema#integer"
			if(o instanceof Integer) return "http://www.w3.org/2001/XMLSchema#integer"
			if(o instanceof Long) return "http://www.w3.org/2001/XMLSchema#integer"

			if(o instanceof Double) return "http://www.w3.org/2001/XMLSchema#double"
			if(o instanceof Float) return "http://www.w3.org/2001/XMLSchema#float"

			if(o instanceof BigInteger) return "http://www.w3.org/2001/XMLSchema#integer"
			if(o instanceof BigDecimal) return "http://www.w3.org/2001/XMLSchema#decimal"

			return "http://www.w3.org/2001/XMLSchema#decimal"
		}

		// not managing date/time primitives yet

		return 'sometype'
	}
}

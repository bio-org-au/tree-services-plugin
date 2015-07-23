package au.org.biodiversity.nsl.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import au.org.biodiversity.nsl.UriNs;

/**
 * An RDF Renderable is a simple DocumentObjectModel for and RDF Document. It is
 * built around the boatree Uri object. To build a renderable, start with a
 * RdfRenderable.Obj object, and add stuff to it.
 * 
 * @author ibis
 * @see Uri
 */

public interface RdfRenderable {
	public static enum CardType {
		Single, Bag, List
	}

	public static class Info {
		Set<UriNs> namespaces = new HashSet<UriNs>();
		Map<Uri, CardType> cardinality = new HashMap<Uri, CardType>();

		public void ns(UriNs ns) {
			this.namespaces.add(ns);
		}

		public void card(Uri k, RdfRenderable vv) {
			CardType cc;
			if (vv instanceof Seq) {
				cc = CardType.List;
			} else if (vv instanceof Bag) {
				cc = CardType.Bag;
			} else {
				cc = CardType.Single;
			}

			if (cardinality.containsKey(k)) {
				if (cc != cardinality.get(k)) {
					throw new IllegalArgumentException("Property "
							+ k.asQNameD()
							+ " has conflicting cardinality types");
				}
			} else {
				cardinality.put(k, cc);
			}
		}
	}

	/**
	 * A tag interface marking types that may permissibly be rendered as a
	 * top-level object in a generated document.
	 * 
	 * @author ibis
	 *
	 */
	public interface Top extends RdfRenderable {
	}

	/**
	 * An RDF object. And RDF object always has a type Uri, may have an id Uri,
	 * and has several predicate Uris which may be single or multi valued. The
	 * values of the predicates are other RdfRenderable objects. An
	 * RDFRenderable, basically, should be structured like a nested document -
	 * you should not build it with reused, duplicate elements.
	 * 
	 * @author ibis
	 * @see Uri
	 */

	public static class Obj extends HashMap<Uri, Obj.Value> implements
			RdfRenderable, Top {

		public static interface Value {
			public boolean isSingleValue();

			public SingleValue asSingleValue();

			public MultiValue asMultiValue();
		};

		public static class SingleValue implements Value {
			public final RdfRenderable v;

			SingleValue(RdfRenderable v) {
				if(v==null) {
					throw new IllegalArgumentException("null");
				}
				this.v = v;
			}

			@Override
			public boolean isSingleValue() {
				return true;
			}

			@Override
			public SingleValue asSingleValue() {
				return this;
			}

			@Override
			public MultiValue asMultiValue() {
				throw new IllegalStateException();
			}
		};

		public static class MultiValue extends ArrayList<RdfRenderable>
				implements Value {
			
			public boolean add(RdfRenderable o) {
				return super.add((RdfRenderable)o);
			}
			
			public void add(int i, RdfRenderable o) {
				super.add(i, (RdfRenderable)o);
			}
			
			@Override
			public boolean isSingleValue() {
				return false;
			}

			@Override
			public SingleValue asSingleValue() {
				throw new IllegalStateException();
			}

			@Override
			public MultiValue asMultiValue() {
				return this;
			}
		};

		protected static Log log = LogFactory.getLog(Obj.class);

		public final Uri id;
		public final Uri typeUri;

		public Obj(Uri typeUri) {
			this.id = null;
			this.typeUri = typeUri;
			if (typeUri == null) {
				throw new IllegalArgumentException(
						"type of objects cannot be null");
			}
			this.addMulti(DomainUtils.uri("rdf", "type"), typeUri);
		}

		public Obj(Uri typeUri, Uri id) {
			this.id = id;
			this.typeUri = typeUri;
			if (typeUri == null) {
				throw new IllegalArgumentException(
						"type of objects cannot be null");
			}
			this.addMulti(DomainUtils.uri("rdf", "type"), typeUri);
		}

		public Uri getId() {
			return id;
		}

		public Uri getTypeUri() {
			return typeUri;
		}

		public void add(Uri k, Uri v) {
			add(k, new Resource(v));
		}

		public void add(Uri k, Number v) {
			add(k, new Primitive(v));
		}

		public void add(Uri k, String v) {
			add(k, new Primitive(v));
		}

		public void add(Uri k, Boolean v) {
			add(k, new Primitive(v));
		}

		public void add(Uri k, Character v) {
			add(k, new Primitive(v));
		}

		public void add(Uri uri, RdfRenderable value) {
			if (containsKey(uri)) {
				throw new IllegalStateException(uri + " already has a value");
			}
			put(uri, new SingleValue(value));
		}

		public void addMulti(Uri k, Uri v) {
			addMulti(k, new Resource(v));
		}

		public void addMulti(Uri k, Number v) {
			addMulti(k, new Primitive(v));
		}

		public void addMulti(Uri k, String v) {
			addMulti(k, new Primitive(v));
		}

		public void addMulti(Uri k, Boolean v) {
			addMulti(k, new Primitive(v));
		}

		public void addMulti(Uri k, Character v) {
			addMulti(k, new Primitive(v));
		}

		public void addMulti(Uri uri, RdfRenderable value) {
			if (!containsKey(uri)) {
				put(uri, new MultiValue());
			} else if (get(uri).isSingleValue()) {
				throw new IllegalStateException(uri
						+ " already has a single value");
			}

			get(uri).asMultiValue().add(value);
		}

		public void getInfo(Info info) {
			if (id != null)
				info.ns(id.nsPart);
			info.ns(typeUri.nsPart);

			for (Uri u : keySet()) {
				info.ns(u.nsPart);
			}

			for (Map.Entry<Uri, Value> e : entrySet()) {
				Uri k = e.getKey();
				Value v = e.getValue();
				if (v == null) {
					// eh?
				} else if (v.isSingleValue()) {
					info.card(k, v.asSingleValue().v);
					v.asSingleValue().v.getInfo(info);
				} else {
					for (RdfRenderable vv : v.asMultiValue()) {
						if (vv != null) {
							info.card(k, vv);
							vv.getInfo(info);
						}
					}
				}
			}
		}

		public String toString() {
			return typeUri.asQName() + "(" + typeUri.asQName() + ","
					+ (id == null ? "NULL" : id.asQName()) + ")"
					+ super.toString();
		}
	}

	/**
	 * An RDF collection. I am calling this "Coll" because the name collides
	 * with java.util.Collection
	 * 
	 * @author ibis
	 */

	public static abstract class Coll extends ArrayList<RdfRenderable>
			implements RdfRenderable, Top {
		
		public void getInfo(Info info) {
			for (RdfRenderable r : this) {
				if (r != null) {
					r.getInfo(info);
				}
			}
		}

		public String toString() {
			return getClass().getSimpleName() + super.toString();
		}
	}

	/**
	 * An RDF Seq collection. Rdf sequences start from 1, so element 0 of the
	 * list is pre-populated with a null. This means that the JsonArray will be
	 * one element shorter than this.size();
	 * 
	 * @author ibis
	 */

	public static class Seq extends Coll implements RdfRenderable {
		{
			add(null);
		}

		public RdfRenderable set(int i, RdfRenderable r) {
			while (size() <= i) {
				add(null);
			}
			return super.set(i, r);
		}
	}

	/**
	 * An RDF Bag collection. Null elements in the bag are skipped, but no
	 * duplicated detection is done.
	 * 
	 * @author ibis
	 */
	public static class Bag extends Coll implements RdfRenderable {
	}

	/**
	 * A primitive type. At present, we support String, Number, Boolean, and
	 * Character.
	 * 
	 * @TODO add support for java.sql.Timestamp etc
	 * @author ibis
	 */

	public static class Primitive implements RdfRenderable {
		public final Object o;

		public Primitive(String o) {
			if (o == null)
				throw new IllegalArgumentException();
			this.o = o;
		}

		public Primitive(Number o) {
			if (o == null)
				throw new IllegalArgumentException();
			this.o = o;
		}

		public Primitive(Boolean o) {
			if (o == null)
				throw new IllegalArgumentException();
			this.o = o;
		}

		public Primitive(Character o) {
			if (o == null)
				throw new IllegalArgumentException();
			this.o = o;
		}

		public void getInfo(Info info) {
		}

		public String toString() {
			return getClass().getSimpleName() + "\"" + o + "\"";
		}
	}
	
	public static class Literal implements RdfRenderable {
		public final Uri uri;
		public final String literal;
		
		public Literal(Uri uri, String literal) {
			if (uri == null)
				throw new IllegalArgumentException();
			this.uri = uri;
			this.literal = literal;
		}
		
		public void getInfo(Info info) {
			info.ns(uri.nsPart);
		}

		public String toString() {
			return getClass().getSimpleName() + "\"" + literal + "\"^^" + uri.asQNameD();
		}
	}

	/**
	 * An RDF resource. A pointer to a URI.
	 * 
	 * @author ibis
	 */

	public static class Resource implements RdfRenderable {
		public final Uri uri;

		public Resource(Uri uri) {
			if (uri == null)
				throw new IllegalArgumentException();
			this.uri = uri;
		}

		public void getInfo(Info info) {
			info.ns(uri.nsPart);
		}

		public String toString() {
			return "<" + uri + ">";
		}
	}

	/**
	 * Visit the object and put all namespaces used by all URIs in the bag. You
	 * will usually use a Set() of some kind.
	 * 
	 * @param bag
	 *            place to accumulate the namespaces
	 */

	public void getInfo(Info info);

}

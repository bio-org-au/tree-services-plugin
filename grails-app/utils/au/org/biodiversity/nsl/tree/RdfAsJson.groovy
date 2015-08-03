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

package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.UriNs;

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull;import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonArray

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

class RdfAsJson implements RdfSerializer {
	final Log log = LogFactory.getLog(RdfAsJson.class)

	final RdfRenderable.Top r

	RdfAsJson(RdfRenderable.Top r) {
		this.r = r
	}

	void render(Writer w) {
		JsonObject o = new JsonObject()

		JsonObject context = new JsonObject()

		RdfRenderable.Info info = new RdfRenderable.Info()
		r.getInfo(info)

		context.add('id', new JsonPrimitive('@id'))
		context.add('type', new JsonPrimitive('@type'))
		context.add('@vocab', new JsonPrimitive(DomainUtils.getBoatreeNs().uri))
		for(UriNs ns: info.namespaces) {
			if(!DomainUtils.isBlank(ns) && !DomainUtils.isBoatree(ns)) {
				context.add(ns.label, new JsonPrimitive(ns.uri))
			}
		}

		for(Map.Entry<Uri, RdfRenderable.CardType> e: info.cardinality) {
			if(e.getValue() == RdfRenderable.CardType.Bag) {
				JsonObject predColl = new JsonObject()
				predColl.add('@container', new JsonPrimitive('@set'))
				context.add(e.getKey().asQNameD(), predColl)
			}
			else
			if(e.getValue() == RdfRenderable.CardType.List) {
				JsonObject predColl = new JsonObject()
				predColl.add('@container', new JsonPrimitive('@list'))
				context.add(e.getKey().asQNameD(), predColl)
			}
		}

		o.add('@context', context)

		if(r instanceof RdfRenderable.Obj) {
			renderObjectProperties((RdfRenderable.Obj)r, o)
		}
		else {
			o.add('value', asJson(r))
		}

		GsonBuilder gb = new GsonBuilder()
		gb.setPrettyPrinting()
		Gson gson = gb.create()

		gson.toJson(o, w)
		w.flush()
	}

	JsonElement asJson(RdfRenderable r) {
		if(r instanceof RdfRenderable.Obj) return objAsJson((RdfRenderable.Obj)r)
		if(r instanceof RdfRenderable.Bag) return bagAsJson((RdfRenderable.Bag)r)
		if(r instanceof RdfRenderable.Seq) return seqAsJson((RdfRenderable.Seq)r)
		if(r instanceof RdfRenderable.Primitive) return primitiveAsJson((RdfRenderable.Primitive)r)
		if(r instanceof RdfRenderable.Resource) return resourceAsJson((RdfRenderable.Resource)r)
		throw new IllegalStateException()
	}

	JsonElement objAsJson(RdfRenderable.Obj r) {
		JsonObject o = new JsonObject()

		renderObjectProperties(r, o)

		return o
	}

	private void renderObjectProperties(RdfRenderable.Obj r, JsonObject o) {
		if (r.getId()) {
			o.add("id", new JsonPrimitive(r.getId().asQNameD()))
		}

		if (r.getTypeUri()) {
			o.add("type", new JsonPrimitive(r.getTypeUri().asQNameD()))
		}

		for (Map.Entry<Uri, Collection<RdfRenderable>> e : r.entrySet()) {
			if (!e.getKey())
				continue
			String k

			if (e.getValue() == null) {
				o.add(e.getKey().asQNameD(), JsonNull.INSTANCE)
			} else if (e.getValue().isSingleValue()) {
				o.add(e.getKey().asQNameD(), asJson(e.getValue().asSingleValue().v))
			} else {
				JsonArray v = new JsonArray()
				for (RdfRenderable rr : e.getValue().asMultiValue()) {
					v.add(asJson(rr))
				}
				o.add(e.getKey().asQNameD(), v)
			}
		}
	}

	JsonElement bagAsJson(RdfRenderable.Bag r) {
		JsonArray a = new JsonArray()
		for (RdfRenderable rr : r) {
			if (rr == null) {
				// unordered - don't include nulls
			} else {
				a.add(asJson(rr))
			}
		}
		return a
	}

	JsonElement seqAsJson(RdfRenderable.Seq r) {
		JsonArray a = new JsonArray()
		for(int i = 1; i<r.size(); i++) {
			if (r.get(i) == null) {
				a.add(JsonNull.INSTANCE)
			} else {
				a.add(asJson(r.get(i)))
			}
		}
		return a
	}

	JsonElement primitiveAsJson(RdfRenderable.Primitive r) {
		Object o = r.o
		if (o == null) {
			return JsonNull.INSTANCE
		} else if (o instanceof Number) {
			return new JsonPrimitive((Number) o)
		} else if (o instanceof String) {
			return new JsonPrimitive((String) o)
		} else if (o instanceof Boolean) {
			return new JsonPrimitive((Boolean) o)
		} else if (o instanceof Character) {
			return new JsonPrimitive((Character) o)
		} else
			throw new IllegalStateException("cant handle JSON type "
			+ o.getClass().getName())
	}

	JsonElement resourceAsJson(RdfRenderable.Resource r) {
		JsonObject o = new JsonObject()
		o.add('id', new JsonPrimitive(r.uri.asQNameD()))
		return o
	}
}

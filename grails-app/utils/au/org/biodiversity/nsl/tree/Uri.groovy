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

import java.util.regex.Pattern
import grails.converters.JSON
import au.org.biodiversity.nsl.UriNs
import au.org.biodiversity.nsl.tree.DomainUtils;

/**
 * A uri made from an ns part and an id part. Note that equality and identity is based on
 * the NS id, not on what happens when you concatenate the two parts.
 */

public class Uri implements Comparable<Uri> {
	/** guaranteed not to be null */
	public final UriNs nsPart
	/** guaranteed not to be null */
	public final String idPart

	public Uri(UriNs nsPart, Long idPart) {
		this(nsPart, idPart.toString())
	}

	public Uri(UriNs nsPart, Integer idPart) {
		this(nsPart, idPart.toString())
	}

	public Uri(UriNs nsPart, String idPart) {
		if(nsPart==null) throw new IllegalArgumentException()
		this.nsPart = nsPart
		this.idPart = idPart ?: ''
	}

	public String toString() {
		if(nsPart.id == 0) {
			return "##<${idPart}>##"
		}
		else if(nsPart.id == 1) {
			return "##:${idPart}##"
		}
		else {
			return "##${nsPart.label + ":" + idPart}##"
		}
	}

	public String asQNameIfOk() {
		return isQNameOk() ? asQName() : asUri();
	}

	public String asQNameDIfOk() {
		return isQNameOk() ? asQNameD() : asUri();
	}

	public String asUri() {
		return nsPart.uri + idPart
	}

	public String asQName() {
		checkQNameOk()
		return nsPart.label + ":" + idPart
	}

	/**
	 * returns the url as a qname, but assuming that the default namespace is 'boatree-voc'. Note that we are not checking that 
	 * the idpart is ok for a qname
	 */
	public String asQNameD() {
		if(DomainUtils.isBoatree(nsPart) || DomainUtils.isBlank(nsPart)) {
			return idPart
		}
		else {
			return nsPart.label + ":" + idPart
		}
	}

	/**
	 * NCName as per http://www.w3.org/TR/1999/REC-xml-names-19990114/ . More or less. I am missing several weird unicode things, but meh.
	 */

	private static final ThreadLocal<Pattern> NCNAME_PATTERN = new ThreadLocal<Pattern>() {
		protected Pattern initialValue() {
			return Pattern.compile('^[\\p{Alpha}_][\\p{Alnum}_.\\-]*$')
		}
	}

	private boolean isQNameOk() {
		return NCNAME_PATTERN.get().matcher(idPart).matches()
	}

	private void checkQNameOk() {
		if(!isQNameOk()) {
			throw new IllegalStateException("id part \"${idPart}\" cannot be used in a QName")
		}
	}

	public String asCssClass() {
		return (DomainUtils.isBlank(nsPart) ? '' : toCssClass(nsPart.label) + ' ' ) +  toCssClass(idPart);
	}

	public static String toCssClass(o) {
		if(o==null) return '';

		return o.toString()
				.replaceAll(~/[^\-_a-zA-Z0-9]+/, '-')
				.replaceAll(~/([a-z])([A-Z])/, { "${it[1]}-${it[2]}" })
				.replaceAll(~/-+/, '-')
				.toLowerCase();
	}

	public int hashCode() {
		return nsPart.id ^ idPart.hashCode()
	}

	public boolean equals(Object o) {
		if(!(o instanceof Uri)) return false
		return idPart.equals(((Uri)o).idPart) && nsPart.id == ((Uri)o).nsPart.id
	}

	public int compareTo(Uri o) {
		if(o == null) return 1
		if(this.is(o)) return 0
		if(nsPart.id == o.nsPart.id)
			return idPart.compareTo(o.idPart)
		else
			return nsPart.getUri().compareTo(o.nsPart.getUri())
	}

	/**
	 * @deprecated I have implemented asBoolean instead
	 */
	public boolean isBlank() {
		return !asBoolean()
	}

	public boolean asBoolean() {
		return !DomainUtils.isBlank(nsPart) || !idPart.isEmpty()
	}

	public String getEncoded() {
		if(isBlank()) {
			return ""
		}

		return URLEncoder.encode(asUri())
	}
}

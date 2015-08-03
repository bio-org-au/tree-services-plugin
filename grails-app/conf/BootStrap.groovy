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

import grails.converters.JSON

class BootStrap {
    def init = { servletContext ->

        JSON.registerObjectMarshaller(au.org.biodiversity.nsl.tree.Uri) { au.org.biodiversity.nsl.tree.Uri u ->
            [
                    'class': u?.getClass()?.getSimpleName(),
                    'id': u?.idPart,
                    // zero is a magic number, it is the 'no namespace' namespace
                    // ns of zero menas that the uri is simply the entire string in the idPart
                    'ns': ((u?.nsPart?.id ?: 0) != 0) ? u?.nsPart?.label : null,
                    'uri': u?.asUri(),
                    'qname': u?.isQNameOk() ? u?.asQName() : null,
                    'css' : u?.asCssClass()
            ]
        }

        JSON.registerObjectMarshaller(au.org.biodiversity.nsl.tree.Message) { au.org.biodiversity.nsl.tree.Message m ->
            [
                    'class': m?.getClass()?.getSimpleName(),
                    msg: m?.msg?.name(),
                    message: m?.getLocalisedString(),
                    params: m?.args,
                    nested: m?.nested
            ]
        }

        JSON.registerObjectMarshaller(au.org.biodiversity.nsl.tree.ServiceException) { au.org.biodiversity.nsl.tree.ServiceException e ->
            [
                    'class': e.getClass().getSimpleName(),
                    message: e.getLocalizedMessage(),
                    msg: e.msg,
                    stackTrace: e.getStackTrace().findAll { it.className.startsWith('au.org.biodiversity.')  }
            ]
        }

    }

    def destroy = {
    }
}

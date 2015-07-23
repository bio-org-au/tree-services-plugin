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

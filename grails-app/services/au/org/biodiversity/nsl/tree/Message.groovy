package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Instance
import au.org.biodiversity.nsl.Name
import au.org.biodiversity.nsl.Reference
import grails.util.Holders
import org.springframework.context.i18n.LocaleContextHolder

class Message {
    // this won't work - this object is not a service
    // def messageSource
    public final Msg msg
    public final List args = []
    public final List nested = []

    public Message(Msg msg, args = null) {
        this.msg = msg
        if (!msg) throw new IllegalArgumentException('null msg')
        if (args) {
            if (args instanceof Collection) {
                this.args.addAll((Collection) args)
            } else {
                this.args.add args
            }

        }
    }

    public static Message makeMsg(Msg msg, args = null) {
        return new Message(msg, args)
    }

    // this is for loggers, etc. The web pages should use a template to render these objects.

    public String toString() {
        return getLocalisedString()
    }

    public String getLocalisedString() {
        StringBuilder sb = new StringBuilder()
        buildNestedString(sb, 0)
        return sb.toString()
    }

    protected void buildNestedString(StringBuilder sb, int depth) {
        sb.append(getSpringMessage())

        for (Object o : nested) {
            sb.append('\n')
            for (int i = 0; i < depth + 1; i++) {
                sb.append('\t')
            }

            if (o instanceof Message) {
                ((Message) o).buildNestedString(sb, depth + 1);
            } else if (o instanceof ServiceException) {
                sb.append(o.getClass().getSimpleName());
                sb.append(": ");
                ((ServiceException) o).msg.buildNestedString(sb, depth + 1);
            } else {
                for (int i = 0; i < depth + 1; i++) {
                    sb.append('\t')
                }
                sb.append(o)
            }
        }
    }

    private static String prefTitle(Name name) {
        return name.simpleName ?: name.fullName;
    }

    private static String prefTitle(Reference reference) {
        return reference.abbrevTitle ?: reference.displayTitle ?: reference.title ?: reference.citation;
    }

    private static String prefTitle(Instance instance) {
        String p = instance.page ? " p. ${instance.page}" : '';
        String pq = instance.pageQualifier ? " [${instance.pageQualifier}]" : '';
        return "${prefTitle(instance.name)} in ${prefTitle(instance.reference)}${p}${pq}";
    }

    private static String prefTitle(Arrangement arrangement) {
        arrangement.label ?: arrangement.arrangementType.uriId;
    }

    public String getSpringMessage() {
        // this does the job of deciding what our domain objects ought to look like when they appear in
        def args2 = args.collect { Object it ->
            if (it instanceof Message) {
                // in general, the args of a message should not contain nested messages.
                // Only nested messages ought to contain nested messages.
                // If a message is created with an arg that is a message with nested messages,
                // then the formatting (tabs and newlines) will be messed up.
                Message message = it as Message;
                return message.toString();
            } else if (it instanceof Arrangement) {
                return "${prefTitle((Arrangement) it)}|${it.id}";
            } else if (it instanceof Name) {
                return "${prefTitle(it as Name)}|${it.id}";
            } else if (it instanceof Reference) {
                return "${prefTitle(it as Reference)}|${it.id}";
            } else if (it instanceof Instance) {
                return "${prefTitle(it as Instance)}|${it.id}";
            } else if (it.hasProperty('id')) {
                return "${it.getClass().getSimpleName()}|${it.id}";
            } else {
                return it;
            }
        }

        return Holders.applicationContext.getMessage(
                msg.getKey(),
                args2.toArray(),
                msg.getKey() + args2,
                LocaleContextHolder.getLocale())
    }
}
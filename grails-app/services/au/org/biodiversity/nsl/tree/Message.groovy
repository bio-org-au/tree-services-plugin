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

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Instance
import au.org.biodiversity.nsl.Name
import au.org.biodiversity.nsl.Reference
import grails.util.Holders
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.i18n.LocaleContextHolder

class Message implements MessageSourceResolvable {
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

    String[] getCodes() {
        String[] codes = new String[1];
        codes[0] = msg.key;
        return codes;
    }

    /**
     * Return the array of arguments to be used to resolve this message.
     * @return an array of objects to be used as parameters to replace
     * placeholders within the message text
     * @see java.text.MessageFormat
     */
    Object[] getArguments() {
        Object[] av = new Object[args.size()];
        for(int i = 0; i<args.size(); i++) av[i] = args.get(i);
        return av;
    }

    /**
     * Return the default message to be used to resolve this message.
     * @return the default message, or {@code null} if no default
     */
    String getDefaultMessage() {
        StringBuilder s = new StringBuilder();
        s.append(msg.name());
        if(args && args.size() > 0) {
            for(int i = 0; i<args.size(); i++) {
                s.append(i==0? ": {" : ", {");
                s.append(i);
                s.append("}");
            };
        }
        return s.toString();
    }

    protected void buildNestedString(StringBuilder sb, int depth) {
        sb.append(getSpringMessage())

        for (Object o : nested) {
            sb.append('\n')
            for (int i = 0; i < depth + 1; i++) {
                sb.append('\t')
            }

            if (o instanceof Message) {
                if(depth > 10) {
                    sb.append("(Depth exceeded)");
                }
                else {
                    ((Message) o).buildNestedString(sb, depth + 1);
                }
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
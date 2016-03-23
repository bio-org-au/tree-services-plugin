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

import org.springframework.context.MessageSourceResolvable

// this is unnecessarily complicated and not at all groovy. I should be passing around maps, which groovy can convert straight into JSON.

class ServiceException extends Exception implements MessageSourceResolvable {
    public final Message msg // the top level item. We make this an item so that it can be rendered using the same code as all the other items.

    protected ServiceException(Message msg) {
        super(msg?.msg?.name())
        if (!msg) throw new IllegalArgumentException('null message')
        this.msg = msg
    }

    public static void raise(Message msg) throws ServiceException {
        throw new ServiceException(msg)
    }

    public static Message makeMsg(Msg msg, args = null) {
        return Message.makeMsg(msg, args)
    }

    public String getMessage() {
        return msg.toString();
    }

    public String getLocalizedMessage() {
        return msg.getLocalisedString();
    }

    @Override
    String[] getCodes() {
        return msg.getCodes();
    }

    @Override
    Object[] getArguments() {
        msg.getArguments();
    }

    @Override
    String getDefaultMessage() {
        return msg.getDefaultMessage();
    }
}

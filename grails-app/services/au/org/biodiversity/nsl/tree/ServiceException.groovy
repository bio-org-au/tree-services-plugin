package au.org.biodiversity.nsl.tree
// this is unnecessarily complicated and not at all groovy. I should be passing around maps, which groovy can convert straight into JSON.

class ServiceException extends Exception {
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
}

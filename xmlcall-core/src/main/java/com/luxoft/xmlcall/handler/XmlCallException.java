package com.luxoft.xmlcall.handler;

public class XmlCallException extends RuntimeException
{
    private final String xmlMessage;

    public XmlCallException(String message, String xmlMessage) {
        super(message);
        this.xmlMessage = xmlMessage;
    }

    public XmlCallException(String message, String xmlMessage, Throwable cause) {
        super(message, cause);
        this.xmlMessage = xmlMessage;
    }

    public String getXmlMessage() {
        return xmlMessage;
    }
}

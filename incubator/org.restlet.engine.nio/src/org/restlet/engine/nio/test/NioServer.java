package org.restlet.engine.nio.test;

import org.restlet.Context;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;

public class NioServer {

    public static void main(String[] args) throws Exception {
        // Engine.getInstance().getRegisteredServers()
        // .add(0, new org.restlet.ext.jetty.HttpServerHelper(null));
        // Engine.getInstance().getRegisteredServers()
        // .add(0, new org.restlet.ext.grizzly.HttpServerHelper(null));
        // Engine.getInstance()
        // .getRegisteredServers()
        // .add(0,
        // new org.restlet.engine.http.connector.HttpServerHelper(
        // null));
        Engine.getInstance().getRegisteredServers()
                .add(0, new org.restlet.engine.nio.HttpServerHelper(null));

        Server server = new Server(new Context(), Protocol.HTTP, 9999);
        // server.getContext().getParameters().add("tracing", "true");
        server.getContext().getParameters().add("minThreads", "20");
        server.getContext().getParameters().add("lowThreads", "30");
        server.getContext().getParameters().add("maxThreads", "40");
        server.getContext().getParameters().add("maxQueued", "20");
        server.setNext(HelloServerResource.class);
        server.start();

    }

}
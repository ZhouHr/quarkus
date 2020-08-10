package io.quarkus.qrs.runtime.handlers;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.arc.Arc;
import io.quarkus.qrs.runtime.core.QrsRequestContext;
import io.quarkus.qrs.runtime.mapping.RequestMapper;
import io.quarkus.qrs.runtime.mapping.RuntimeResource;
import io.quarkus.qrs.runtime.spi.BeanFactory;

public class ResourceLocatorHandler implements RestHandler {

    private final Map<Class<?>, Map<String, RequestMapper<RuntimeResource>>> resourceLocatorHandlers = new HashMap<>();

    @Override
    public void handle(QrsRequestContext requestContext) throws Exception {
        Object locator = requestContext.getResult();
        Class<?> locatorClass;
        if (locator instanceof Class) {
            locatorClass = (Class<?>) locator;
            try {
                locator = Arc.container().instance(locatorClass).get();
            } catch (Exception e) {
                requestContext.setThrowable(
                        new RuntimeException("Could not instantiate resource bean " + locatorClass
                                + " make sure it has a bean defining annotation", e));
                return;
            }
        } else {
            locatorClass = locator.getClass();
        }
        Map<String, RequestMapper<RuntimeResource>> target = findTarget(locatorClass);
        if (target == null) {
            requestContext.setThrowable(
                    new RuntimeException("Resource locator method returned object that was not a resource: " + locator));
            return;
        }
        RequestMapper<RuntimeResource> mapper = target.get(requestContext.getMethod());
        if (mapper == null) {
            requestContext.setThrowable(
                    new WebApplicationException(Response.status(HttpResponseStatus.NOT_FOUND.code()).build()));
            return;
        }
        RequestMapper.RequestMatch<RuntimeResource> res = mapper
                .map(requestContext.getRemaining().isEmpty() ? "/" : requestContext.getRemaining());
        if (res == null) {
            requestContext.setThrowable(
                    new WebApplicationException(Response.status(HttpResponseStatus.NOT_FOUND.code()).build()));
            return;
        }
        requestContext.saveUriMatchState();
        requestContext.setRemaining(res.remaining);
        requestContext.setPathParamValues(res.pathParamValues);
        requestContext.setEndpointInstance(new FixedBeanInstance(locator));
        requestContext.setResult(null);
        requestContext.restart(res.value);

    }

    private Map<String, RequestMapper<RuntimeResource>> findTarget(Class<?> locatorClass) {
        if (locatorClass == Object.class || locatorClass == null) {
            return null;
        }
        Map<String, RequestMapper<RuntimeResource>> res = resourceLocatorHandlers.get(locatorClass);
        if (res != null) {
            return res;
        }
        for (Class<?> iface : locatorClass.getInterfaces()) {
            res = resourceLocatorHandlers.get(iface);
            if (res != null) {
                return res;
            }
            for (Class<?> i : iface.getInterfaces()) {
                Map<String, RequestMapper<RuntimeResource>> located = findTarget(i);
                if (located != null) {
                    return located;
                }
            }
        }
        return findTarget(locatorClass.getSuperclass());
    }

    public void addResource(Class<?> resourceClass, Map<String, RequestMapper<RuntimeResource>> requestMapper) {
        Class<?> c = resourceClass;
        resourceLocatorHandlers.put(c, requestMapper);

    }

    private static class FixedBeanInstance implements BeanFactory.BeanInstance<Object> {
        private final Object locator;

        public FixedBeanInstance(Object locator) {
            this.locator = locator;
        }

        @Override
        public Object getInstance() {
            return locator;
        }

        @Override
        public void close() {

        }
    }
}
package io.quarkus.qrs.runtime.spi;

import io.quarkus.qrs.runtime.core.QrsRequestContext;

public interface BeanFactory<T> {

    /**
     * Creates an endpoint instance outside the scope of a request
     */
    BeanInstance<T> createInstance();

    /**
     * Creates an endpoint instance inside the scope of a request
     */
    BeanInstance<T> createInstance(QrsRequestContext requestContext);

    interface BeanInstance<T> extends AutoCloseable {

        T getInstance();

        void close();
    }

}
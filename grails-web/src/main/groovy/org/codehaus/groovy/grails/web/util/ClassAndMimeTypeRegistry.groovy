/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.util

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import groovy.transform.CompileStatic
import org.codehaus.groovy.grails.web.mime.MimeType
import org.codehaus.groovy.grails.web.mime.MimeTypeProvider
import org.springframework.util.ClassUtils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Abstract class for class that maintains a registry of mappings MimeType,Class and a particular object type. Used by RendererRegistry and DataBindingSourceRegistry
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
abstract class ClassAndMimeTypeRegistry<R extends MimeTypeProvider, K> {

    private Map<Class, Collection<R >> registeredObjectsByType = new ConcurrentHashMap<>();
    private Map<MimeType, R> defaultObjectsByMimeType = new ConcurrentHashMap<>();
    private Map<K, R > resolvedObjectCache = new ConcurrentLinkedHashMap.Builder<K, R>()
        .initialCapacity(500)
        .maximumWeightedCapacity(1000)
        .build();

    void registerDefault(MimeType mt, R object) {
        defaultObjectsByMimeType.put(mt, object)
    }

    void addToRegisteredObjects(Class targetType, R object) {
        Collection<R> rendererList = getRegisteredObjects(targetType)
        rendererList.add(object)
    }

    Collection<R> getRegisteredObjects(Class targetType) {
        final registeredObjects = registeredObjectsByType.get(targetType)
        if (registeredObjects == null) {
            registeredObjects = new ConcurrentLinkedQueue<R>()
            registeredObjectsByType.put(targetType, registeredObjects)
        }
        return registeredObjects
    }

    def R findMatchingObjectForMimeType(MimeType mimeType, object) {
        if(object == null) return null

        final clazz = object instanceof Class ? (Class)object : object.getClass()

        final K cacheKey = createCacheKey(clazz, mimeType)
        R renderer = (R)resolvedObjectCache.get(cacheKey)
        if (renderer == null) {

            Class currentClass = clazz
            while(currentClass != null) {

                renderer = findRendererForType(currentClass, mimeType)
                if (renderer) {
                    resolvedObjectCache.put(cacheKey, renderer)
                    return renderer
                }
                if (currentClass == Object) break
                currentClass = currentClass.getSuperclass()
            }

            final interfaces = ClassUtils.getAllInterfaces(object)
            for(i in interfaces) {
                renderer = findRendererForType(i, mimeType)
                if (renderer) break
            }

            if (renderer == null) {
                renderer = (R)defaultObjectsByMimeType.get(mimeType)
            }
        }
        if (renderer != null) {
            resolvedObjectCache.put(cacheKey, renderer)
        }
        return renderer
    }

    protected R findRendererForType(Class currentClass, MimeType mimeType) {
        R findObject = null
        final objectList = registeredObjectsByType.get(currentClass)
        if (objectList) {
            findObject = (R)objectList.find { MimeTypeProvider r -> r.mimeTypes.any { MimeType mt -> mt  == mimeType  }}
        }
        findObject
    }

    void removeFromCache(Class type, MimeType mimeType) {
        final key = createCacheKey(type, mimeType)
        resolvedObjectCache.remove(key)
    }

    abstract K createCacheKey(Class type, MimeType mimeType)
}
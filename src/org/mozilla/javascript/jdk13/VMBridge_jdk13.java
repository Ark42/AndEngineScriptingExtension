/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-2000
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.javascript.jdk13;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.andengine.util.exception.MethodNotSupportedException;
import org.mozilla.javascript.AnonymousSubclassAdapter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.InterfaceAdapter;
import org.mozilla.javascript.Kit;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject.Slot;
import org.mozilla.javascript.VMBridge;

public class VMBridge_jdk13 extends VMBridge
{
    private ThreadLocal<Object[]> contextLocal = new ThreadLocal<Object[]>();

    @Override
    protected Object getThreadContextHelper()
    {
        // To make subsequent batch calls to getContext/setContext faster
        // associate permanently one element array with contextLocal
        // so getContext/setContext would need just to read/write the first
        // array element.
        // Note that it is necessary to use Object[], not Context[] to allow
        // garbage collection of Rhino classes. For details see comments
        // by Attila Szegedi in
        // https://bugzilla.mozilla.org/show_bug.cgi?id=281067#c5

        Object[] storage = contextLocal.get();
        if (storage == null) {
            storage = new Object[1];
            contextLocal.set(storage);
        }
        return storage;
    }

    @Override
    protected Context getContext(Object contextHelper)
    {
        Object[] storage = (Object[])contextHelper;
        return (Context)storage[0];
    }

    @Override
    protected void setContext(Object contextHelper, Context cx)
    {
        Object[] storage = (Object[])contextHelper;
        storage[0] = cx;
    }

    @Override
    protected ClassLoader getCurrentThreadClassLoader()
    {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    protected boolean tryToMakeAccessible(Object accessibleObject)
    {
        if (!(accessibleObject instanceof AccessibleObject)) {
            return false;
        }
        AccessibleObject accessible = (AccessibleObject)accessibleObject;
        if (accessible.isAccessible()) {
            return true;
        }
        try {
            accessible.setAccessible(true);
        } catch (Exception ex) { }

        return accessible.isAccessible();
    }

    @Override
    protected Object getInterfaceProxyHelper(ContextFactory cf,
                                             Class<?>[] interfaces)
    {
        // XXX: How to handle interfaces array withclasses from different
        // class loaders? Using cf.getApplicationClassLoader() ?
        ClassLoader loader = interfaces[0].getClassLoader();
        Class<?> cl = Proxy.getProxyClass(loader, interfaces);
        Constructor<?> c;
        try {
            c = cl.getConstructor(new Class[] { InvocationHandler.class });
        } catch (NoSuchMethodException ex) {
            // Should not happen
            throw Kit.initCause(new IllegalStateException(), ex);
        }
        return c;
    }
    
    @Override
    protected Object getAnonymousSubclassProxyHelper(ContextFactory cf,
    		Class<?> anonymoussubclass)
    {
    	// XXX: How to handle interfaces array withclasses from different
    	// class loaders? Using cf.getApplicationClassLoader() ?
    	ClassLoader loader = anonymoussubclass.getClassLoader();
    	Class<?> cl = Proxy.getProxyClass(loader, anonymoussubclass.getInterfaces());
    	Constructor<?> c;
    	try {
    		c = cl.getConstructor(new Class[] { InvocationHandler.class });
    	} catch (NoSuchMethodException ex) {
    		// Should not happen
    		throw Kit.initCause(new IllegalStateException(), ex);
    	}
    	return c;
    }

    @Override
    protected Object newInterfaceProxy(Object proxyHelper,
                                       final ContextFactory cf,
                                       final InterfaceAdapter adapter,
                                       final Object target,
                                       final Scriptable topScope)
    {
        Constructor<?> c = (Constructor<?>)proxyHelper;

        InvocationHandler handler = new InvocationHandler() {
                public Object invoke(Object proxy,
                                     Method method,
                                     Object[] args)
                {
                	try {
                		return adapter.invoke(cf, target, topScope, method, args);
	                } catch (MethodNotSupportedException e) {
						Context.reportWarning(ScriptRuntime.getMessage1("msg.undefined.function.interface", method.getName()));
						final Class<?> resultType = method.getReturnType();
						if(resultType == Void.TYPE) {
							return null;
						} else {
							return Context.jsToJava(null, resultType);
						}
					}
                }
            };
        Object proxy;
        try {
            proxy = c.newInstance(new Object[] { handler });
        } catch (InvocationTargetException ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        } catch (IllegalAccessException ex) {
            // Shouls not happen
            throw Kit.initCause(new IllegalStateException(), ex);
        } catch (InstantiationException ex) {
            // Shouls not happen
            throw Kit.initCause(new IllegalStateException(), ex);
        }
        return proxy;
    }

	@Override
	protected Object newAnonymousSubclassProxy(final Object proxyHelper, final ContextFactory cf, final AnonymousSubclassAdapter adapter, final Object target, final Scriptable topScope, final Object pO, final Slot[] pSlots) {
		Constructor<?> c = (Constructor<?>) proxyHelper;

		InvocationHandler handler = new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				try {
					return adapter.invoke(cf, target, topScope, method, args, pO, pSlots);
				} catch (MethodNotSupportedException e) {
					Context.reportWarning(ScriptRuntime.getMessage1("msg.undefined.function.interface", method.getName()));
					final Class<?> resultType = method.getReturnType();
					if(resultType == Void.TYPE) {
						return null;
					} else {
						return Context.jsToJava(null, resultType);
					}
				}
			}
		};
		Object proxy;
		try {
			proxy = c.newInstance(new Object[] { handler });
		} catch (InvocationTargetException ex) {
			throw Context.throwAsScriptRuntimeEx(ex);
		} catch (IllegalAccessException ex) {
			// Shouls not happen
			throw Kit.initCause(new IllegalStateException(), ex);
		} catch (InstantiationException ex) {
			// Shouls not happen
			throw Kit.initCause(new IllegalStateException(), ex);
		}
		return proxy;
	}

    @Override
    protected boolean isVarArgs(Member member) {
      return false;
    }
}

/*
 * %W% %E%
 * 
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.jmx.mbeanserver;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import javax.management.*; 


import com.sun.jmx.trace.Trace;
import sun.reflect.misc.ReflectUtil;

/**
 * Implements the MBeanInstantiator interface. Provides methods for
 * instantiating objects, finding the class given its name and using
 * different class loaders, deserializing objects in the context of a
 * given class loader.
 *
 * @since 1.5
 * @since.unbundled JMX RI 1.2
 */
public class MBeanInstantiator {

    private final ModifiableClassLoaderRepository clr; 
    //    private MetaData meta = null;

    /** The name of this class to be used for tracing */
    private final static String dbgTag = "MBeanInstantiator";

    MBeanInstantiator(ModifiableClassLoaderRepository clr) {
	this.clr = clr;
    }

  
    /** 
     * This methods tests if the MBean class makes it possible to 
     * instantiate an MBean of this class in the MBeanServer.
     * e.g. it must have a public constructor, be a concrete class...
     */    
    public void testCreation(Class c) throws NotCompliantMBeanException {
	Introspector.testCreation(c);
    }

    /**
     * Loads the class with the specified name using this object's 
     * Default Loader Repository. 
     **/
    public Class findClassWithDefaultLoaderRepository(String className)
	throws ReflectionException {

	Class theClass;
	if (className == null) {
	    throw new RuntimeOperationsException(new 
		IllegalArgumentException("The class name cannot be null"), 
                             "Exception occurred during object instantiation");
	}

	try {
	    if (clr == null) throw new ClassNotFoundException(className);
	    theClass = clr.loadClass(className);
	}
	catch (ClassNotFoundException ee) {
	    throw new ReflectionException(ee, 
       "The MBean class could not be loaded by the default loader repository");
	}
	
	return theClass;
    }


    /**
     * Gets the class for the specified class name using the MBean 
     * Interceptor's classloader
     */
    public Class findClass(String className, ClassLoader loader) 
        throws ReflectionException {
   
        return loadClass(className,loader);
    }

    /**
     * Gets the class for the specified class name using the specified 
     * class loader
     */
    public Class findClass(String className, ObjectName aLoader) 
        throws ReflectionException, InstanceNotFoundException  {
	Class theClass = null;

        if (aLoader == null)  
	    throw new RuntimeOperationsException(new 
		IllegalArgumentException(), "Null loader passed in parameter");

        // Retrieve the class loader from the repository
        ClassLoader loader = null;
        synchronized(this) {
	    if (clr!=null) 
		loader = clr.getClassLoader(aLoader);
        }
        if (loader == null) {
            throw new InstanceNotFoundException("The loader named " + 
		       aLoader + " is not registered in the MBeanServer");
        }     
	return findClass(className,loader);
    }


    /**
     * Return an array of Class corresponding to the given signature, using
     * the specified class loader.
     */
    public Class[] findSignatureClasses(String signature[],
					ClassLoader loader)
	throws  ReflectionException {

	if (signature == null) return null;
	final ClassLoader aLoader = (ClassLoader) loader;
	final int length= signature.length;
	final Class tab[]=new Class[length]; 

	if (length == 0) return tab;
	try {
	    for (int i= 0; i < length; i++) {
		// Start handling primitive types (int. boolean and so 
		// forth)
		//
		
		final Class primCla = primitiveClasses.get(signature[i]);
		if (primCla != null) {
		    tab[i] = primCla;
		    continue;
		}

		// Ok we do not have a primitive type ! We need to build 
		// the signature of the method
		//
		if (aLoader != null) {
		    // We need to load the class through the class 
		    // loader of the target object.
		    // 
		    tab[i] = Class.forName(signature[i], false, aLoader);
		} else {
		    // Load through the default class loader
		    //
		    tab[i] = findClass(signature[i], 
				       this.getClass().getClassLoader());
		}
	    }
	} catch (ClassNotFoundException e) {
	    debugX("findSignatureClasses",e);
	    throw new ReflectionException(e, 
		      "The parameter class could not be found");
	} catch (RuntimeException e) {
	    debugX("findSignatureClasses",e);
	    throw e; 
	}
	return tab;
    }


    /**
     * Instantiates an object given its class, using its empty constructor.
     * The call returns a reference to the newly created object.
     */
    public Object instantiate(Class theClass) 
	throws ReflectionException, MBeanException {
        Object moi = null;


	// ------------------------------ 
	// ------------------------------
        Constructor cons = findConstructor(theClass, null);
        if (cons == null) {
            throw new ReflectionException(new 
		NoSuchMethodException("No such constructor"));
        }
        // Instantiate the new object
        try {
	    ReflectUtil.checkPackageAccess(theClass);
            moi= cons.newInstance();
        } catch (InvocationTargetException e) {
            // Wrap the exception.
            Throwable t = e.getTargetException();
            if (t instanceof RuntimeException) {
                throw new RuntimeMBeanException((RuntimeException)t, 
                   "RuntimeException thrown in the MBean's empty constructor");
            } else if (t instanceof Error) {
                throw new RuntimeErrorException((Error) t, 
                   "Error thrown in the MBean's empty constructor");
            } else {
                throw new MBeanException((Exception) t, 
                   "Exception thrown in the MBean's empty constructor");  
            }
        } catch (NoSuchMethodError error) {
            throw new ReflectionException(new 
		NoSuchMethodException("No constructor"), 
					  "No such constructor");
        } catch (InstantiationException e) {
            throw new ReflectionException(e, 
            "Exception thrown trying to invoke the MBean's empty constructor");
        } catch (IllegalAccessException e) {
            throw new ReflectionException(e, 
            "Exception thrown trying to invoke the MBean's empty constructor");
        } catch (IllegalArgumentException e) {
            throw new ReflectionException(e, 
            "Exception thrown trying to invoke the MBean's empty constructor");
        }
        return moi;

    }

   

   /**
     * Instantiates an object given its class, the parameters and
     * signature of its constructor The call returns a reference to
     * the newly created object.
     */
    public Object instantiate(Class theClass, Object params[],
			      String signature[], ClassLoader loader)
        throws ReflectionException, MBeanException {
        // Instantiate the new object

	// ------------------------------
	// ------------------------------
        final Class[] tab;
        Object moi= null;
        try {
	    // Build the signature of the method
	    //
	    ClassLoader aLoader= (ClassLoader) theClass.getClassLoader();
	    // Build the signature of the method
	    //
	    tab =
		((signature == null)?null:
		 findSignatureClasses(signature,aLoader));
	}
        // Exception IllegalArgumentException raised in Jdk1.1.8
        catch (IllegalArgumentException e) {
            throw new ReflectionException(e,
		    "The constructor parameter classes could not be loaded");
        }

        // Query the metadata service to get the right constructor
        Constructor cons = null;
        cons = findConstructor(theClass, tab);

        if (cons == null) {
            throw new ReflectionException(new
		NoSuchMethodException("No such constructor"));
        }
        try {
            ReflectUtil.checkPackageAccess(theClass);
            moi = cons.newInstance(params);
        }
        catch (NoSuchMethodError error) {
            throw new ReflectionException(new
		NoSuchMethodException("No such constructor found"),
					  "No such constructor" );
        }
        catch (InstantiationException e) {
            throw new ReflectionException(e,
                "Exception thrown trying to invoke the MBean's constructor");
        }
        catch (IllegalAccessException e) {
            throw new ReflectionException(e,
                "Exception thrown trying to invoke the MBean's constructor");
        }
        catch (InvocationTargetException e) {
            // Wrap the exception.
            Throwable th = e.getTargetException();
            if (th instanceof RuntimeException) {
                throw new RuntimeMBeanException((RuntimeException)th,
		      "RuntimeException thrown in the MBean's constructor");
            } else if (th instanceof Error) {
                throw new RuntimeErrorException((Error) th,
                      "Error thrown in the MBean's constructor");
            } else {
                throw new MBeanException((Exception) th,
                      "Exception thrown in the MBean's constructor");
            }
        }
        return moi;
    }

    /**
     * De-serializes a byte array in the context of a classloader.
     *
     * @param loader the classloader to use for de-serialization
     * @param data The byte array to be de-sererialized.
     *
     * @return  The de-serialized object stream.
     *
     * @exception OperationsException Any of the usual Input/Output related
     * exceptions.
     */
    public ObjectInputStream deserialize(ClassLoader loader, byte[] data)
	throws OperationsException {

        // Check parameter validity    
        if (data == null) {
            throw new  RuntimeOperationsException(new 
		IllegalArgumentException(), "Null data passed in parameter");
        }
        if (data.length == 0) {
	    throw new  RuntimeOperationsException(new 
		IllegalArgumentException(), "Empty data passed in parameter");
        }
 
	// Object deserialization      
        ByteArrayInputStream bIn;
        ObjectInputStream    objIn;
        String               typeStr;

        bIn   = new ByteArrayInputStream(data);
        try {
            objIn = new ObjectInputStreamWithLoader(bIn,loader);
        } catch (IOException e) {
            throw new OperationsException(
                     "An IOException occurred trying to de-serialize the data");
        }
 
        return objIn;    
    }

    /**
     * De-serializes a byte array in the context of a given MBean class loader.
     * <P>The class loader is the one that loaded the class with name
     * "className".
     * <P>The name of the class loader to be used for loading the specified
     * class is specified. If null, a default one has to be provided (for a
     * MBean Server, its own class loader will be used).
     *
     * @param className The name of the class whose class loader should 
     *  be used for the de-serialization.
     * @param data The byte array to be de-sererialized.
     * @param loaderName The name of the class loader to be used for loading
     * the specified class. If null, a default one has to be provided (for a
     * MBean Server, its own class loader will be used).
     *
     * @return  The de-serialized object stream.
     *
     * @exception InstanceNotFoundException The specified class loader MBean is
     * not found.          
     * @exception OperationsException Any of the usual Input/Output related
     * exceptions.
     * @exception ReflectionException The specified class could not be loaded
     * by the specified class loader.
     */
    public ObjectInputStream deserialize(String className,
					 ObjectName loaderName,
					 byte[] data,
					 ClassLoader loader)
	throws InstanceNotFoundException,
	       OperationsException,
	       ReflectionException  {

        // Check parameter validity
        if (data == null) {
            throw new  RuntimeOperationsException(new 
		IllegalArgumentException(), "Null data passed in parameter");
        }
        if (data.length == 0) {
            throw new  RuntimeOperationsException(new 
		IllegalArgumentException(), "Empty data passed in parameter");
        }
        if (className == null) {
            throw new  RuntimeOperationsException(new 
	     IllegalArgumentException(), "Null className passed in parameter");
        }       
        Class theClass = null;
        if (loaderName == null) {
            // Load the class using the agent class loader
	    theClass = findClass(className, loader);
        
        } else {
            // Get the class loader MBean
	    try {
		ClassLoader instance = null;
		
		if (clr!=null)  
		    instance = clr.getClassLoader(loaderName);
		if (instance == null) 
		    throw new ClassNotFoundException(className);
		theClass = Class.forName(className, false, instance);
            }
            catch (ClassNotFoundException e) {
                throw new ReflectionException(e, 
                               "The MBean class could not be loaded by the " + 
		               loaderName.toString() + " class loader");
            }
        }
 
        // Object deserialization
        ByteArrayInputStream bIn;
        ObjectInputStream    objIn;
        String               typeStr;
        
        bIn   = new ByteArrayInputStream(data);
        try {
            objIn = new ObjectInputStreamWithLoader(bIn,
					   theClass.getClassLoader());
        } catch (IOException e) {
            throw new OperationsException(
                    "An IOException occurred trying to de-serialize the data");
        }
        
        return objIn;
    }


    /**
     * Instantiates an object using the list of all class loaders registered
     * in the MBean Interceptor
     * (using its {@link javax.management.loading.ClassLoaderRepository}).
     * <P>The object's class should have a public constructor.
     * <P>It returns a reference to the newly created object.
     * <P>The newly created object is not registered in the MBean Interceptor.
     *
     * @param className The class name of the object to be instantiated.    
     *
     * @return The newly instantiated object.    
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or the
     * <CODE>java.lang.Exception</CODE> that occurred when trying to invoke the
     * object's constructor.
     * @exception MBeanException The constructor of the object has thrown an
     * exception
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: the className passed in
     * parameter is null.
     */
    public Object instantiate(String className)
	throws ReflectionException,
	MBeanException {

	return instantiate(className, (Object[]) null, (String[]) null, null);
    }



    /**
     * Instantiates an object using the class Loader specified by its
     * <CODE>ObjectName</CODE>.
     * <P>If the loader name is null, a default one has to be provided (for a
     * MBean Server, the ClassLoader that loaded it will be used).
     * <P>The object's class should have a public constructor.
     * <P>It returns a reference to the newly created object.
     * <P>The newly created object is not registered in the MBean Interceptor.
     *
     * @param className The class name of the MBean to be instantiated.    
     * @param loaderName The object name of the class loader to be used.
     *
     * @return The newly instantiated object.    
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or the
     * <CODE>java.lang.Exception</CODE> that occurred when trying to invoke the
     * object's constructor.
     * @exception MBeanException The constructor of the object has thrown an
     * exception.
     * @exception InstanceNotFoundException The specified class loader is not
     * registered in the MBeanServerInterceptor.
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: the className passed in
     * parameter is null.
     */
    public Object instantiate(String className, ObjectName loaderName, 
			      ClassLoader loader) 
        throws ReflectionException, MBeanException,
	       InstanceNotFoundException {

	return instantiate(className, loaderName, (Object[]) null, 
			   (String[]) null, loader);
    }


    /**
     * Instantiates an object using the list of all class loaders registered
     * in the MBean server
     * (using its {@link javax.management.loading.ClassLoaderRepository}).
     * <P>The object's class should have a public constructor.
     * <P>The call returns a reference to the newly created object.
     * <P>The newly created object is not registered in the MBean Interceptor.
     *
     * @param className The class name of the object to be instantiated.
     * @param params An array containing the parameters of the constructor to
     * be invoked.
     * @param signature An array containing the signature of the constructor to
     * be invoked.     
     *
     * @return The newly instantiated object.    
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or the
     * <CODE>java.lang.Exception</CODE> that occurred when trying to invoke the
     * object's constructor.  
     * @exception MBeanException The constructor of the object has thrown an
     * exception
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: the className passed in
     * parameter is null.
     */    
    public Object instantiate(String className,
			      Object params[],
			      String signature[],
			      ClassLoader loader) 
        throws ReflectionException,
	MBeanException {

	Class theClass = findClassWithDefaultLoaderRepository(className);
	return instantiate(theClass, params, signature, loader);
    }



    /**
     * Instantiates an object. The class loader to be used is identified by its
     * object name.
     * <P>If the object name of the loader is null, a default has to be
     * provided (for example, for a MBean Server, the ClassLoader that loaded
     * it will be used).
     * <P>The object's class should have a public constructor.
     * <P>The call returns a reference to the newly created object.
     * <P>The newly created object is not registered in the MBean server.
     *
     * @param className The class name of the object to be instantiated.
     * @param params An array containing the parameters of the constructor to
     * be invoked.
     * @param signature An array containing the signature of the constructor to
     * be invoked.     
     * @param loaderName The object name of the class loader to be used.
     *
     * @return The newly instantiated object.    
     *
     * @exception ReflectionException Wraps a
     * <CODE>java.lang.ClassNotFoundException</CODE> or the
     * <CODE>java.lang.Exception</CODE> that occurred when trying to invoke the
     * object's constructor.  
     * @exception MBeanException The constructor of the object has thrown an
     * exception
     * @exception InstanceNotFoundException The specified class loader is not
     * registered in the MBean Interceptor. 
     * @exception RuntimeOperationsException Wraps a
     * <CODE>java.lang.IllegalArgumentException</CODE>: the className passed in
     * parameter is null.
     */    
    public Object instantiate(String className,
			      ObjectName loaderName,
			      Object params[],
			      String signature[],
			      ClassLoader loader) 
        throws ReflectionException,
	       MBeanException,
	InstanceNotFoundException {

	// ------------------------------ 
	// ------------------------------
	Class theClass;
	
	if (loaderName == null) {
	    theClass = findClass(className, loader);
	} else {
	    theClass = findClass(className, loaderName);	
	}       
	return instantiate(theClass, params, signature, loader);
    }


    /**
     * Return the Default Loader Repository used by this instantiator object.
     **/
    public ModifiableClassLoaderRepository getClassLoaderRepository() {
	return clr;
    }

    /**
     * Load a class with the specified loader, or with this object
     * class loader if the specified loader is null.
     **/
    static Class loadClass(String className, ClassLoader loader) 
        throws ReflectionException {
   
        Class theClass = null;
	if (className == null) {
	    throw new RuntimeOperationsException(new 
		IllegalArgumentException("The class name cannot be null"), 
                              "Exception occurred during object instantiation");
	} 
	try {
	    if (loader == null) 
		loader = MBeanInstantiator.class.getClassLoader();
	    if (loader != null) {
		theClass = Class.forName(className, false, loader);
	    } else {
		theClass = Class.forName(className);
	    }
	} catch (ClassNotFoundException e) {
	    throw new ReflectionException(e, 
	    "The MBean class could not be loaded by the context classloader");
	}
        return theClass;
    }


    
    /**
     * Load the classes specified in the signature with the given loader, 
     * or with this object class loader.
     **/
    static Class[] loadSignatureClasses(String signature[],
					ClassLoader loader)
	throws  ReflectionException {
	    
	if (signature == null) return null;
	final ClassLoader aLoader = 
   	   (loader==null?MBeanInstantiator.class.getClassLoader():loader);
	final int length= signature.length;
	final Class tab[]=new Class[length]; 

	if (length == 0) return tab;
	try {
	    for (int i= 0; i < length; i++) {
		// Start handling primitive types (int. boolean and so 
		// forth)
		//
		
		final Class primCla = primitiveClasses.get(signature[i]);
		if (primCla != null) {
		    tab[i] = primCla;
		    continue;
		}

		// Ok we do not have a primitive type ! We need to build 
		// the signature of the method
		//
		// We need to load the class through the class 
		// loader of the target object.
		// 
		tab[i] = Class.forName(signature[i], false, aLoader);
	    }
	} catch (ClassNotFoundException e) {
	    debugX("findSignatureClasses",e);
	    throw new ReflectionException(e, 
		      "The parameter class could not be found");
	} catch (RuntimeException e) {
	    debugX("findSignatureClasses",e);
	    throw e; 
	}
	return tab;
    }
    
    private Constructor<?> findConstructor(Class<?> c, Class<?>[] params) {
        try {
            return c.getConstructor(params);
        } catch (Exception e) {
            return null;
        }
    }

    // TRACES & DEBUG
    //---------------
    
    private static boolean isTraceOn() {
        return Trace.isSelected(Trace.LEVEL_TRACE, Trace.INFO_MBEANSERVER);
    }

    private static void trace(String clz, String func, String info) {
        Trace.send(Trace.LEVEL_TRACE, Trace.INFO_MBEANSERVER, clz, func, info);
    }

    private static void trace(String func, String info) {
        trace(dbgTag, func, info);
    }

    private static boolean isDebugOn() {
        return Trace.isSelected(Trace.LEVEL_DEBUG, Trace.INFO_MBEANSERVER);
    }

    private static void debug(String clz, String func, String info) {
        Trace.send(Trace.LEVEL_DEBUG, Trace.INFO_MBEANSERVER, clz, func, info);
    }

    private static void debug(String func, String info) {
        debug(dbgTag, func, info);
    }

    private static void debugX(String func,Throwable e) {
	if (isDebugOn()) {
	    final StringWriter s = new StringWriter();
	    e.printStackTrace(new PrintWriter(s));
	    final String stack = s.toString();
	    
	    debug(dbgTag,func,"Exception caught in "+ func+"(): "+e);
	    debug(dbgTag,func,stack);
	    
	    // java.lang.System.err.println("**** Exception caught in "+
	    //				 func+"(): "+e);
	    // java.lang.System.err.println(stack);
	}
    }
    
    private static final Map<String, Class<?>> primitiveClasses = Util.newMap();
    static {
        for (Class<?> c : new Class[] {byte.class, short.class, int.class,
                                       long.class, float.class, double.class,
                                       char.class, boolean.class})
            primitiveClasses.put(c.getName(), c);
    }
}

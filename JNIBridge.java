package bitter.jnibridge;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;

public class JNIBridge
{
	static native Object invoke(long ptr, Class clazz, Method method, Object[] args);
	static native void   delete(long ptr);

	static Object newInterfaceProxy(final long ptr, final Class[] interfaces)
	{
		return Proxy.newProxyInstance(JNIBridge.class.getClassLoader(), interfaces, new InterfaceProxy(ptr));
	}

	static void disableInterfaceProxy(final Object proxy)
	{
		((InterfaceProxy) Proxy.getInvocationHandler(proxy)).disable();
	}

	private static class InterfaceProxy implements InvocationHandler
	{
		private Object m_InvocationLock = new Object[0];
		private long m_Ptr;

		public InterfaceProxy(final long ptr) { m_Ptr = ptr; }

		private static Object defaultInvoke(Object proxy, Throwable t, Method m, Object[] args) throws Throwable {
			Class<?> klass = m.getDeclaringClass();
			if (args == null) {
				args = new Object[0];
			}
			try {
				Object lookup;
				Method in_method;
				Method unreflect_method;
				Method bind_to_method;
				Method invoke_with_arguments_method;
				try {
					Class<?> lookup_class = Class.forName("java.lang.invoke.MethodHandles$Lookup");
					Constructor<?> con = lookup_class.getDeclaredConstructor(Class.class, Integer.TYPE);
					con.setAccessible(true);
					lookup = con.newInstance(
							klass, (Integer)lookup_class.getDeclaredField("PRIVATE").get(null));
					in_method = lookup_class.getDeclaredMethod("in", Class.class);
					unreflect_method = lookup_class.getDeclaredMethod(
							"unreflectSpecial", Method.class, Class.class);
					Class<?> method_handle_class = Class.forName("java.lang.invoke.MethodHandle");
					bind_to_method = method_handle_class.getDeclaredMethod("bindTo", Object.class);
					invoke_with_arguments_method =
							method_handle_class.getDeclaredMethod("invokeWithArguments", List.class);
				} catch (ReflectiveOperationException r) {
					// Something went wrong with setup! The expected classes weren't present.
					t.addSuppressed(r);
					throw t;
				}
				return invoke_with_arguments_method.invoke(
						bind_to_method.invoke(
								unreflect_method.invoke(in_method.invoke(lookup, klass), m, klass),
								proxy),
						Arrays.asList(args));
			} catch (InvocationTargetException e) {
				// Default method was called but it threw an exception!
				throw e.getCause();
			}
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
		{
			synchronized (m_InvocationLock)
			{
				try {
					if (m_Ptr == 0)
						return null;
					return JNIBridge.invoke(m_Ptr, method.getDeclaringClass(), method, args);
				} catch (NoSuchMethodError e) {
					if ((method.getModifiers() & Modifier.ABSTRACT) == 0) {
						// We are default. Try to call the default impl.
						return defaultInvoke(proxy, e, method, args);
					} else {
						// Doesn't seem to be a default method!
						throw e;
					}
				}
			}
		}

		public void finalize()
		{
			synchronized (m_InvocationLock)
			{
				if (m_Ptr == 0)
					return;
				JNIBridge.delete(m_Ptr);
			}
		}

		public void disable()
		{
			synchronized (m_InvocationLock)
			{
				m_Ptr = 0;
			}
		}
	}
}

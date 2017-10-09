package bitter.jnibridge;

import java.lang.reflect.*;
import java.lang.invoke.*;

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

		private static final class DefaultInvoker {
			private static Object invoke(Object proxy, Throwable t, Method m, Object[] args) throws Throwable {
				try {
					if (args == null)
						args = new Object[0];
					Class<?> k = m.getDeclaringClass();
					Constructor<MethodHandles.Lookup> con =
							MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Integer.TYPE);
					con.setAccessible(true);
					MethodHandles.Lookup lookup = con.newInstance(k, MethodHandles.Lookup.PRIVATE);
					return lookup.in(k).unreflectSpecial(m, k).bindTo(proxy).invokeWithArguments(args);
				} catch (NoClassDefFoundError ncdfe) {
					t.addSuppressed(ncdfe);
					throw t;
				}
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
						return DefaultInvoker.invoke(proxy, e, method, args);
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

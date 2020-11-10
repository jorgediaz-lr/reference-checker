
	GetModels getModels = new GetModels(out);

	try {
		getModels.run();
	}
	catch (Throwable t) {
		t.printStackTrace();
	}

	import com.liferay.portal.kernel.dao.jdbc.DataAccess;
	import com.liferay.portal.kernel.dao.orm.DynamicQuery;
	import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
	import com.liferay.portal.kernel.repository.model.RepositoryModel;
	import com.liferay.portal.kernel.util.PortalClassLoaderUtil;

	import java.io.PrintWriter;

	import java.lang.reflect.Field;
	import java.lang.reflect.Method;
	import java.lang.reflect.Modifier;

	import java.sql.Connection;
	import java.sql.PreparedStatement;
	import java.sql.ResultSet;
	import java.sql.SQLException;

	import java.util.ArrayList;
	import java.util.List;
	import java.util.Map;

	/**
	 * @author Jorge Díaz
	 */
	public class GetModels {
		public GetModels(PrintWriter out) {
			this.out = out;
		}

		public void run() throws Exception {

			persistedModelLocalServiceRegistry =
				getPersistedModelLocalServiceRegistry();

			if (persistedModelLocalServiceRegistry == null) {
				out.println("persistedModelLocalServiceRegistry is not available");

				return;
			}

			List<String> classNamesList = new ArrayList();

			for (String className : getClassNames()) {
				if (!className.contains(".model.")) {
				//	out.println("Ignoring " + className);

					continue;
				}

				classNamesList.add(className);
			}

			Map<String,String> classNamesMap = new LinkedHashMap();
			Map<String,String> tablesMap = new LinkedHashMap();

			for (String className : classNamesList) {
				String realClassName = className;
				int pos = className.indexOf(".UserPersonalSite");

				if (pos != -1) {
					realClassName = className.substring(0, pos) + ".User";
				}

				Class<?> classLiferayModelImpl = null;

				try {
					classLiferayModelImpl = getLiferayModelImpl(realClassName);
				}
				catch (Throwable t) {
					out.println("Ignoring " + className + " due to exception: " + t);
				}

				if (classLiferayModelImpl == null) {
					continue;
				}

				String modelTableName;

				try {
					Field field = getDeclaredField(
						classLiferayModelImpl, "TABLE_NAME");

					modelTableName = (String)field.get(null);
				}
				catch (Exception e) {
					out.println(
						"Error accessing to " + classLiferayModelImpl.getName() +
							"#TABLE_NAME",
						e);

					continue;
				}

				classNamesMap.put(className,modelTableName);
				tablesMap.put(modelTableName, className);
			}

			out.println(formatMapOutput(classNamesMap));
			out.println(formatMapOutput(tablesMap));
		}

		protected static formatMapOutput(Map map) {
			String mapOutput = map.toString();
			mapOutput = mapOutput.replace('[','[\n\t"');
			mapOutput = mapOutput.replace(']','"\n]');
			mapOutput = mapOutput.replace(', ','",\n\t"');
			mapOutput = mapOutput.replace(':','" : "');
			return mapOutput;
		}

		protected static Field getDeclaredField(Class<?> clazz, String name)
				throws Exception {

			Field field = clazz.getDeclaredField(name);

			field.setAccessible(true);

			int modifiers = field.getModifiers();

			if ((modifiers & _STATIC_FINAL) == _STATIC_FINAL) {
				_modifiersField.setInt(field, modifiers - Modifier.FINAL);
			}

			return field;
		}

		protected Class<?> getLiferayModelImplClass(
				ClassLoader classLoader, String liferayModelImpl)
			throws ClassNotFoundException {

			if (liferayModelImpl == null) {
				return null;
			}

			liferayModelImpl = liferayModelImpl + "ModelImpl";

			liferayModelImpl = liferayModelImpl.replace(
				"ImplModelImpl", "ModelImpl");

			Class<?> clazz = classLoader.loadClass(liferayModelImpl);

			//out.println("loaded class: " + clazz + " from classloader: " + classLoader);

			return clazz;
		}

		protected static Object getPrivateField(Object object, String fieldName)
			throws Exception {

			Class<?> clazz = object.getClass();

			Field field = getDeclaredField(clazz, fieldName);

			return field.get(object);
		}

		protected static String getWrappedModelImpl(DynamicQuery dynamicQuery) {
			try {
				Object detachedCriteria = getPrivateField(
					dynamicQuery, "_detachedCriteria");

				Object criteria = getPrivateField(detachedCriteria, "impl");

				return (String)getPrivateField(criteria, "entityOrClassName");
			}
			catch (Throwable t) {
				t.printStackTrace();
			}

			return null;
		}

		protected List<String> getClassNames() {

			List<String> classNames = new ArrayList();

			Connection conn = null;
			PreparedStatement ps = null;
			ResultSet rs = null;

			try {
				conn = DataAccess.getConnection();

				ps = conn.prepareStatement(
					"select distinct value from ClassName_ order by value");

				ps.setQueryTimeout(10);

				rs = ps.executeQuery();

				while (rs.next()) {
					String value = rs.getString("value");

					classNames.add(value);
				}
			}
			finally {
				DataAccess.cleanUp(conn, ps, rs);
			}

			return classNames;
		}

		protected Class<?> getLiferayModelImpl(String className)
			throws ClassNotFoundException {

			ClassLoader classLoader;
			DynamicQuery dynamicQuery;
			Object service = persistedModelLocalServiceRegistry.getPersistedModelLocalService(className);

			if (service == null) {
				classLoader = PortalClassLoaderUtil.getClassLoader();
				dynamicQuery = newDynamicQueryFromPortal(className);
			}
			else {
				Class<?> serviceClass = service.getClass();

				classLoader = serviceClass.getClassLoader();

				dynamicQuery = service.dynamicQuery();
			}

			if (dynamicQuery == null) {
				return null;
			}

			String liferayModelImpl = getWrappedModelImpl(dynamicQuery);

			return getLiferayModelImplClass(classLoader, liferayModelImpl);
		}

		protected Object getPersistedModelLocalServiceRegistry() {
			ClassLoader classLoader = PortalClassLoaderUtil.getClassLoader();

			Class<?> persistedModelLocalServiceRegistryUtil = null;

			try {
				/* 7.x */
				persistedModelLocalServiceRegistryUtil = classLoader.loadClass(
					"com.liferay.portal.kernel.service." +
						"PersistedModelLocalServiceRegistryUtil");
			}
			catch (Throwable t) {
				System.err.println(t.toString());
			}

			if (persistedModelLocalServiceRegistryUtil == null) {
				try {
					/* 6.x */
					persistedModelLocalServiceRegistryUtil = classLoader.loadClass(
						"com.liferay.portal.service." +
							"PersistedModelLocalServiceRegistryUtil");
				}
				catch (Throwable t) {
					System.err.println(t.toString());
				}
			}

			if (persistedModelLocalServiceRegistryUtil == null) {
				return null;
			}

			try {
				Method getPersistedModelLocalServiceRegistry =
					persistedModelLocalServiceRegistryUtil.getMethod(
						"getPersistedModelLocalServiceRegistry");

				return getPersistedModelLocalServiceRegistry.invoke(null);
			}
			catch (Throwable t) {
				System.err.println(t.toString());
			}

			return null;
		}

		protected DynamicQuery newDynamicQueryFromPortal(String className) {
			try {
				ClassLoader classLoader = PortalClassLoaderUtil.getClassLoader();

				if (classLoader == null) {
					Class<?> clazz = getClass();

					classLoader = clazz.getClassLoader();
				}

				Class<?> classInterface = classLoader.loadClass(className);

				if (RepositoryModel.class.isAssignableFrom(classInterface)) {
					return null;
				}

				return DynamicQueryFactoryUtil.forClass(
					classInterface, null, classInterface.getClassLoader());
			}
			catch (ClassNotFoundException cnfe) {
				return null;
			}
		}

		protected PrintWriter out;

		protected Object persistedModelLocalServiceRegistry = null;

		private static final int _STATIC_FINAL = Modifier.STATIC + Modifier.FINAL;

		private static final Method _cloneMethod;
		private static final Field _modifiersField;

		static {
			try {
				_cloneMethod = Object.class.getDeclaredMethod("clone");

				_cloneMethod.setAccessible(true);

				_modifiersField = Field.class.getDeclaredField("modifiers");

				_modifiersField.setAccessible(true);
			}
			catch (Exception exception) {
				throw new ExceptionInInitializerError(exception);
			}
		}

	}
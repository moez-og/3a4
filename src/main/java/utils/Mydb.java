package utils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import utils.db.DbSchema;

public class Mydb {

    private final String url="jdbc:mysql://localhost:3306/fintokhrej";
    private final String user="root";
    private final String password="";

    public Connection getConnection() {
        Connection cn = getOrCreateConnection();
        return nonClosingProxy(cn);
    }

    private Connection connection;
    private static Mydb instance;
    
    public Mydb(){
        // Lazy-init via getOrCreateConnection(), but try to connect once early for fast feedback.
        try {
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("✓ Connexion à la base de données réussie!");

            // Small schema bootstrap (idempotent).
            DbSchema.ensureNotificationsSchema(connection);
        } catch (SQLException e) {
            System.err.println("✗ Erreur de connexion à la base de données: " + e.getMessage());
            connection = null;
        }
    }
    
    public static Mydb getInstance(){
        if(instance==null)
            instance=new Mydb();
        return instance;
    }

    private synchronized Connection getOrCreateConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, password);

                // Ensure schema if we had to reconnect.
                DbSchema.ensureNotificationsSchema(connection);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur de connexion à la base de données: " + e.getMessage(), e);
        }
        return connection;
    }

    /**
     * Returns a Connection wrapper that ignores close() calls.
     * This prevents accidental closing of the shared connection via try-with-resources.
     */
    private static Connection nonClosingProxy(Connection target) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("close".equals(name)) {
                    return null; // no-op
                }
                if ("isClosed".equals(name)) {
                    return target.isClosed();
                }
                if ("unwrap".equals(name)) {
                    return target.unwrap((Class<?>) args[0]);
                }
                if ("isWrapperFor".equals(name)) {
                    return target.isWrapperFor((Class<?>) args[0]);
                }
                return method.invoke(target, args);
            }
        };
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            handler
        );
    }
}

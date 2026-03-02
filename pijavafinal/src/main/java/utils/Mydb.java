package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class Mydb {

    private final String url="jdbc:mysql://localhost:3306/fintokhrej";
    private final String user="root";
    private final String password="";

    // ✅ APRÈS — vérifie et reconnecte si nécessaire
    public Connection getConnection() throws SQLException {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connection = DriverManager.getConnection(url, user, password);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    private Connection connection;
    private static Mydb instance;
    
    public Mydb(){
        try {
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("✓ Connexion à la base de données réussie!");
        }catch (SQLException e){
            System.err.println("✗ Erreur de connexion à la base de données: " + e.getMessage());
        }
    }
    
    public static Mydb getInstance(){
        if(instance==null)
            instance=new Mydb();
        return instance;
    }
}

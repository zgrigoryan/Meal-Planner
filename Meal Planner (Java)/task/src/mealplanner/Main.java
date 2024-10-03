package mealplanner;

import java.sql.*;
import java.util.*;

public class Main {

  private static final Scanner scanner = new Scanner(System.in);
  private static Connection connection;

  public static void main(String[] argv) {
    String DB_URL = "jdbc:postgresql://localhost:5432/meals_db";
    String USER = "postgres";
    String PASS = "1111";


    try {
      // establish a database connection
      connection = DriverManager.getConnection(DB_URL, USER, PASS);
      initializeDatabase();
      loadMeals();

      String command;
      do {
        System.out.println("What would you like to do (add, show, exit)?");
        command = scanner.nextLine();

        switch (command) {
          case "add":
            addMeal();
            break;
          case "show":
            showMeals();
            break;
          case "exit":
            System.out.println("Bye!");
            break;
          default:
            System.out.println("What would you like to do (add, show, exit)?");
        }
      } while (!command.equals("exit"));

    } catch (SQLException e) {
      System.err.println("Connection Failed. Check output console.");
      e.printStackTrace();
    } finally {
      try {
        if (connection != null) connection.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  // Create meals and ingredients tables
  private static void initializeDatabase() throws SQLException {
    Statement statement = connection.createStatement();
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS meals (" +
            "category VARCHAR(1024) NOT NULL," +
            "meal VARCHAR(1024) NOT NULL," +
            "meal_id INTEGER NOT NULL" +
            ")");
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS ingredients (" +
            "ingredient VARCHAR(1024) NOT NULL," +
            "ingredient_id INTEGER NOT NULL," +
            "meal_id INTEGER NOT NULL" +
            ")");
    statement.close();
  }

  private static void loadMeals() {
    try {
      Statement statement = connection.createStatement();
      ResultSet rs = statement.executeQuery("SELECT * FROM meals");
      while (rs.next()) {
        int mealId = rs.getInt("meal_id");
        String category = rs.getString("category");
        String meal = rs.getString("meal");

        List<String> ingredients = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT ingredient FROM ingredients WHERE meal_id = ?");
        ps.setInt(1, mealId);
        ResultSet ingredientRs = ps.executeQuery();
        while (ingredientRs.next()) {
          ingredients.add(ingredientRs.getString("ingredient"));
        }
        ps.close();
        ingredientRs.close();
        meals.add(new Meal(category, meal, ingredients));
      }
      rs.close();
      statement.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  //local list for showing the emals
  private static List<Meal> meals = new ArrayList<>();

  private static void showMeals() {
    if (meals.isEmpty()) {
      System.out.println("No meals saved. Add a meal first.");
    } else {
      for (Meal meal : meals) {
        System.out.println("Category: " + meal.getCategory());
        System.out.println("Name: " + meal.getName());
        System.out.println("Ingredients:");
        for (String ingredient : meal.getIngredients()) {
          System.out.println(ingredient);
        }
        System.out.println();
      }
    }
  }


  private static void addMeal() {
    String name;
    String category;
    List<String> ingredients = new ArrayList<>();

    // get meal category
    while (true) {
      System.out.println("Which meal do you want to add (breakfast, lunch, dinner)?");
      category = scanner.nextLine();
      if (isValidCategory(category)) {
        break;
      } else {
        System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
      }
    }

    // get meal name
    while (true) {
      System.out.println("Input the meal's name:");
      name = scanner.nextLine().trim();
      if (isValidName(name)) {
        break;
      } else {
        System.out.println("Wrong format. Use letters only!");
      }
    }

    // get the ingredients
    while (true) {
      System.out.println("Input the ingredients:");
      String ingredientsInput = scanner.nextLine().trim();
      String[] ingredientsArray = ingredientsInput.split(",");
      boolean valid = true;
      ingredients.clear();

      for (String ingredient : ingredientsArray) {
        ingredient = ingredient.trim();
        if (isValidIngredient(ingredient)) {
          ingredients.add(ingredient);
        } else {
          System.out.println("Wrong format. Use letters only!");
          valid = false;
          break;
        }
      }

      if (valid && !ingredients.isEmpty()) {
        break;
      }
    }

    // insert into database
    try {
      int mealId = getNextMealId(); // generate meal id

      String insertMealQuery = "INSERT INTO meals " +
              "(category, meal, meal_id) " +
              "VALUES (?, ?, ?)";
      PreparedStatement mealStmt = connection.prepareStatement(insertMealQuery);
      mealStmt.setString(1, category);
      mealStmt.setString(2, name);
      mealStmt.setInt(3, mealId);
      mealStmt.executeUpdate();

      for (String ingredient : ingredients) {
        int ingredientId = getNextIngredientId(); // generate ingredient id

        String insertIngredientQuery = "INSERT INTO ingredients " +
                "(ingredient, ingredient_id, meal_id) " +
                "VALUES (?, ?, ?)";
        PreparedStatement ingredientStmt = connection.prepareStatement(insertIngredientQuery);
        ingredientStmt.setString(1, ingredient);
        ingredientStmt.setInt(2, ingredientId);
        ingredientStmt.setInt(3, mealId);
        ingredientStmt.executeUpdate();
        ingredientStmt.close();
      }

      mealStmt.close();
      System.out.println("The meal has been added!");

      // add meals to the local list
      meals.add(new Meal(category, name, ingredients));

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  // generate meal and ingredient id by adding 1 to the previous maxID
  private static int getNextMealId() throws SQLException {
    Statement stmt = connection.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT MAX(meal_id) FROM meals");
    int maxId = 0;
    if (rs.next() && rs.getObject(1) != null) {
      maxId = rs.getInt(1);
    }
    stmt.close();
    return maxId + 1;
  }

  private static int getNextIngredientId() throws SQLException {
    Statement stmt = connection.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT MAX(ingredient_id) FROM ingredients");
    int maxId = 0;
    if (rs.next() && rs.getObject(1) != null) {
      maxId = rs.getInt(1);
    }
    stmt.close();
    return maxId + 1;
  }

  // check the format of user input
  private static boolean isValidName(String name) {
    return name.matches("[a-zA-Z ]+");
  }

  private static boolean isValidCategory(String category) {
    return category.equals("breakfast") || category.equals("lunch") || category.equals("dinner");
  }

  private static boolean isValidIngredient(String ingredient) {
    return ingredient.matches("[a-zA-Z ]+");
  }


  // inner Meal class

  static class Meal {
    private final String category;
    private final String name;
    private final List<String> ingredients;

    public Meal(String category, String name, List<String> ingredients) {
      this.category = category;
      this.name = name;
      this.ingredients = ingredients;
    }

    public String getCategory() {
      return category;
    }

    public String getName() {
      return name;
    }

    public List<String> getIngredients() {
      return ingredients;
    }
  }
}
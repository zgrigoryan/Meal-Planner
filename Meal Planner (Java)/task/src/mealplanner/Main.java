package mealplanner;

import java.sql.*;
import java.util.*;

public class Main {

  private static final Scanner scanner = new Scanner(System.in);
  private static Connection connection;

  private static final String[] DAYS_OF_WEEK = {
          "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
  };

  private static Map<String, String> breakfastPlan = new LinkedHashMap<>();
  private static Map<String, String> lunchPlan = new LinkedHashMap<>();
  private static Map<String, String> dinnerPlan = new LinkedHashMap<>();

  private static Map<String, List<String>> mealsByCategory = new HashMap<>();


  public static void main(String[] args) {
    String DB_URL = "jdbc:postgresql://localhost:5432/meals_db";
    String USER = "postgres";
    String PASS = "1111";


    try {
      // establish a database connection
      connection = DriverManager.getConnection(DB_URL, USER, PASS);
      initializeDatabase();

      String command;
      do {
        System.out.println("What would you like to do (add, show, plan, list plan, exit)?");
        command = scanner.nextLine();

        switch (command) {
          case "add":
            addMeal();
            break;
          case "show":
            showMeals();
            break;
          case "list plan":
            listPlan();
            break;
          case "plan":
            planMeals();
            break;
          case "exit":
            System.out.println("Bye!");
            break;
          default:
            System.out.println("What would you like to do (add, show, plan, list plan, exit)?");
        }
      } while (!command.equals("exit"));

    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        if (connection != null) connection.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  // initialize meals, ingredients and plan tables.
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
    // Create the plan table
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS plan (" +
            "day VARCHAR(1024) NOT NULL," +
            "meal_category VARCHAR(1024) NOT NULL," +
            "meal_id INTEGER NOT NULL," +
            "meal_option VARCHAR(1024) NOT NULL" +
            ")");
    statement.close();
  }

  //local list for showing the emals
  private static List<Meal> meals = new ArrayList<>();

  // private method to implement the functionality of adding a meal.
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
      String ingredientsInput = scanner.nextLine();
      String[] ingredientsArray = ingredientsInput.split(",");
      boolean valid = true;
      ingredients.clear();

      for (String ingredient : ingredientsArray) {
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

  private static void showMeals() {
    while (true) {

      System.out.println("Which category do you want to print (breakfast, lunch, dinner)?");
      String inputCategory = scanner.nextLine();

      if (!isValidCategory(inputCategory)) {
        System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
        continue;
      }

      try {
        String query = "SELECT * FROM meals WHERE LOWER(category) = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, inputCategory);

        ResultSet rs = statement.executeQuery();

        List<Meal> mealList = new ArrayList<>();

        while (rs.next()) {
          int id = rs.getInt("meal_id");
          String mealName = rs.getString("meal");

          PreparedStatement ps = connection.prepareStatement(
                  "SELECT ingredient FROM ingredients WHERE meal_id = ?"
          );
          ps.setInt(1, id);
          ResultSet ingredientRs = ps.executeQuery();

          List<String> ingredients = new ArrayList<>();
          while (ingredientRs.next()) {
            ingredients.add(ingredientRs.getString("ingredient"));
          }

          ps.close();
          ingredientRs.close();

          Meal meal = new Meal(inputCategory, mealName, ingredients);
          mealList.add(meal);
        }

        rs.close();
        statement.close();

        if (mealList.isEmpty()) {
          System.out.println("No meals found.");
          return;
        }
        System.out.println("Category: " + inputCategory);

        for (int i = 0; i < mealList.size(); i++) {
          Meal meal = mealList.get(i);

          // add an empty line between meals, except before the first meal
          if (i > 0) {
            System.out.println();
          }

          System.out.println("Name: " + meal.getName());
          System.out.println("Ingredients:");

          for (String ingredient : meal.getIngredients()) {
            System.out.println(ingredient);
          }
        }

        break;

      } catch (SQLException e) {
        e.printStackTrace();
        break;
      }
    }

  }

  private static void planMeals() {
    mealsByCategory.clear();
    String[] categories = {"breakfast", "lunch", "dinner"};

    for (String category : categories) {
      try {
        String query = "SELECT meal FROM meals WHERE LOWER(category) = ? ORDER BY meal";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, category);
        ResultSet rs = statement.executeQuery();

        List<String> mealsList = new ArrayList<>();

        while (rs.next()) {
          String mealName = rs.getString("meal");
          mealsList.add(mealName);
        }

        mealsByCategory.put(category, mealsList);

        rs.close();
        statement.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    // check if there are meals in each category
    if (mealsByCategory.get("breakfast").isEmpty() ||
            mealsByCategory.get("lunch").isEmpty() ||
            mealsByCategory.get("dinner").isEmpty()) {
      return;
    }

    // delete the old plan if it exists
    try {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate("DELETE FROM plan");
      stmt.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    for (String day : DAYS_OF_WEEK) {

      System.out.println(day);
      // Plan breakfast
      planMealForCategory(day, "breakfast", breakfastPlan);
      // Plan lunch
      planMealForCategory(day, "lunch", lunchPlan);
      // Plan dinner
      planMealForCategory(day, "dinner", dinnerPlan);

      System.out.println("Yeah! We planned the meals for " + day + ".");
    }


    savePlanToDatabase();

    for (String day : DAYS_OF_WEEK) {
      System.out.println(day);
      System.out.println("Breakfast: " + breakfastPlan.get(day));
      System.out.println("Lunch: " + lunchPlan.get(day));
      System.out.println("Dinner: " + dinnerPlan.get(day));
      System.out.println();
    }
  }

  private static void planMealForCategory(String day, String category, Map<String, String> mealPlan) {
    List<String> meals = mealsByCategory.get(category);

    List<String> sortedMeals = new ArrayList<>(meals);
    Collections.sort(sortedMeals, String.CASE_INSENSITIVE_ORDER);

    for (String meal : sortedMeals) {
      System.out.println(meal);
    }

    System.out.println();
    String prompt = "Choose the " + category + " for " + day + " from the list above:";

    while (true) {
      System.out.println(prompt);
      String chosenMeal = scanner.nextLine();

      if (meals.contains(chosenMeal)) {
        mealPlan.put(day, chosenMeal);
        break;
      } else {
        System.out.println("This meal doesn’t exist. Choose a meal from the list above.");
      }
    }
  }

  private static void savePlanToDatabase() {
    try {
      String insertPlanQuery = "INSERT INTO plan (day, meal_category, meal_id, meal_option) VALUES (?, ?, ?, ?)";
      PreparedStatement planStmt = connection.prepareStatement(insertPlanQuery);

      for (String day : DAYS_OF_WEEK) {
        // Breakfast
        String breakfast = breakfastPlan.get(day);
        int breakfastId = getMealId(breakfast);
        planStmt.setString(1, day);
        planStmt.setString(2, "breakfast");
        planStmt.setInt(3, breakfastId);
        planStmt.setString(4, breakfast);
        planStmt.addBatch();

        // Lunch
        String lunch = lunchPlan.get(day);
        int lunchId = getMealId(lunch);
        planStmt.setString(1, day);
        planStmt.setString(2, "lunch");
        planStmt.setInt(3, lunchId);
        planStmt.setString(4, lunch);
        planStmt.addBatch();

        // Dinner
        String dinner = dinnerPlan.get(day);
        int dinnerId = getMealId(dinner);
        planStmt.setString(1, day);
        planStmt.setString(2, "dinner");
        planStmt.setInt(3, dinnerId);
        planStmt.setString(4, dinner);
        planStmt.addBatch();
      }

      planStmt.executeBatch();
      planStmt.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private static int getMealId(String mealName) throws SQLException {
    String query = "SELECT meal_id FROM meals WHERE meal = ?";
    PreparedStatement stmt = connection.prepareStatement(query);
    stmt.setString(1, mealName);
    ResultSet rs = stmt.executeQuery();
    int mealId = -1;
    if (rs.next()) {
      mealId = rs.getInt("meal_id");
    }
    rs.close();
    stmt.close();
    return mealId;
  }


  private static void listPlan() {
    try {
      String query = "SELECT * FROM plan";
      PreparedStatement stmt = connection.prepareStatement(query);
      ResultSet rs = stmt.executeQuery();

      if (!rs.isBeforeFirst()) {
        System.out.println("No plan found. Please create a plan first.");
        rs.close();
        stmt.close();
        return;
      }

      Map<String, Map<String, String>> weeklyPlan = new HashMap<>();

      while (rs.next()) {
        String day = rs.getString("day");
        String category = rs.getString("meal_category");
        String mealOption = rs.getString("meal_option");

        weeklyPlan
                .computeIfAbsent(day, k -> new HashMap<>())
                .put(category, mealOption);
      }

      rs.close();
      stmt.close();


      // Iterate over the days in the correct order
      for (String day : DAYS_OF_WEEK) {
        Map<String, String> dayPlan = weeklyPlan.get(day);
        if (dayPlan != null) {
          System.out.println(day);
          System.out.println("Breakfast: " + dayPlan.get("breakfast"));
          System.out.println("Lunch: " + dayPlan.get("lunch"));
          System.out.println("Dinner: " + dayPlan.get("dinner"));
          System.out.println(); // Empty line between days
        }
      }

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
    return category.equalsIgnoreCase("breakfast") || category.equalsIgnoreCase("lunch") || category.equalsIgnoreCase("dinner");
  }

  private static boolean isValidIngredient(String ingredient) {
    return ingredient.matches("[a-zA-Z ]+");
  }

  // Meal class
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

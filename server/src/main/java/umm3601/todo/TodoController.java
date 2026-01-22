package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

//import java.nio.charset.StandardCharsets;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
//import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
//import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Sorts;
//import com.mongodb.client.result.DeleteResult;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

/**
 * Controller that manages requests for info about todos.
 */
public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";
  private static final String API_TODOS_BY_ID = "/api/todos/{id}";
  static final String OWNER_KEY = "owner";
  static final String STATUS_KEY = "status";
  static final String BODY_KEY = "body";
  static final String CATEGORY_KEY = "category";
  //static final String SORT_ORDER_KEY = "sortorder";

  /*private static final int REASONABLE_AGE_LIMIT = 150;
  private static final String ROLE_REGEX = "^(admin|editor|viewer)$";
  public static final String EMAIL_REGEX = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";*/

  private final JacksonMongoCollection<Todo> todosCollection;

  /**
   * Construct a controller for users.
   *
   * @param database the database containing user data
   */
  public TodoController(MongoDatabase database) {
    todosCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Set the JSON body of the response to be the single user
   * specified by the `id` parameter in the request
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodo(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;

    try {
      todo = todosCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested todo id wasn't a legal Mongo Object ID.");
    }
    if (todo == null) {
      throw new NotFoundResponse("The requested todo was not found");
    } else {
      ctx.json(todo);
      ctx.status(HttpStatus.OK);
    }
  }

  /**
   * Set the JSON body of the response to be a list of all the users returned from the database
   * that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    //Bson sortingOrder = constructSortingOrder(ctx);

    // All three of the find, sort, and into steps happen "in parallel" inside the
    // database system. So MongoDB is going to find the users with the specified
    // properties, return those sorted in the specified manner, and put the
    // results into an initially empty ArrayList.
    ArrayList<Todo> matchingTodos = todosCollection
      .find(combinedFilter)
      //.sort(sortingOrder)
      .into(new ArrayList<>());


    // Set the JSON body of the response to be the list of users returned by the database.
    // According to the Javalin documentation (https://javalin.io/documentation#context),
    // this calls result(jsonString), and also sets content type to json
    ctx.json(matchingTodos);

    // Explicitly set the context status to OK
    ctx.status(HttpStatus.OK);
  }

  /**
   * Construct a Bson filter document to use in the `find` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `age`, `company`, and `role` query
   * parameters and constructs a filter document that will match users with
   * the specified values for those fields.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *   to filter the database collection of users
   */
  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters

    if (ctx.queryParamMap().containsKey(OWNER_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(OWNER_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(OWNER_KEY, pattern));
    }
   /* if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      String status = ctx.queryParamAsClass(STATUS_KEY, String.class)
        .check(it -> it.matches(STATUS_KEY), "User must have a status")
        .get();
      filters.add(eq(STATUS_KEY, status));
    }
  */
    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }


  public void getTodosGroupedByOwner(Context ctx) {
    // We'll support sorting the results either by company name (in either `asc` or `desc` order)
    // or by the number of users in the company (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "_id");
    if (sortBy.equals("owner")) {
      sortBy = "_id";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    // The `UserByCompany` class is a simple class that has fields for the company
    // name, the number of users in that company, and a list of user names and IDs
    // (using the `UserIdName` class to store the user names and IDs).
    // We're going to use the aggregation pipeline to group users by company, and
    // then count the number of users in each company. We'll also collect the user
    // names and IDs for each user in each company. We'll then convert the results
    // of the aggregation pipeline to `UserByCompany` objects.

    ArrayList<TodoByOwner> matchingTodos = todosCollection
      // The following aggregation pipeline groups users by company, and
      // then counts the number of users in each company. It also collects
      // the user names and IDs for each user in each company.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, name, and company fields
          new Document("$project", new Document("_id", 1).append("category", 1).append("owner", 1)),
          // Group the users by company, and count the number of users in each company
          new Document("$group", new Document("_id", "$owner")
            // Count the number of users in each company
            .append("count", new Document("$sum", 1))
            // Collect the user names and IDs for each user in each company
            .append("subjects", new Document("$push", new Document("_id", "$_id").append("category", "$category")))),
          // Sort the results. Use the `sortby` query param (default "company")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to UserGroupResult objects
        // (i.e., a list of UserGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        TodoByOwner.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }

  public void getTodosGroupedByCategory(Context ctx) {
    // We'll support sorting the results either by company name (in either `asc` or `desc` order)
    // or by the number of users in the company (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "_id");
    if (sortBy.equals("category")) {
      sortBy = "_id";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    // The `UserByCompany` class is a simple class that has fields for the company
    // name, the number of users in that company, and a list of user names and IDs
    // (using the `UserIdName` class to store the user names and IDs).
    // We're going to use the aggregation pipeline to group users by company, and
    // then count the number of users in each company. We'll also collect the user
    // names and IDs for each user in each company. We'll then convert the results
    // of the aggregation pipeline to `UserByCompany` objects.

    ArrayList<TodoByCategory> matchingTodos = todosCollection

      // The following aggregation pipeline groups users by company, and
      // then counts the number of users in each company. It also collects
      // the user names and IDs for each user in each company.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, name, and company fields
          new Document("$project", new Document("_id", 1).append("owner", 1).append("category", 1)),
          // Group the users by company, and count the number of users in each company
          new Document("$group", new Document("_id", "$category")
            // Count the number of users in each company
            .append("count", new Document("$sum", 1))
            // Collect the user names and IDs for each user in each company
            .append("subjects", new Document("$push", new Document("_id", "$_id").append("owner", "$owner")))),
          // Sort the results. Use the `sortby` query param (default "company")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to UserGroupResult objects
        // (i.e., a list of UserGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        TodoByCategory.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }


   /**
   * Construct a Bson sorting document to use in the `sort` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `sortby` and `sortorder` query
   * parameters and constructs a sorting document that will sort users by
   * the specified field in the specified order. If the `sortby` query
   * parameter is not present, it defaults to "name". If the `sortorder`
   * query parameter is not present, it defaults to "asc".
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *   used to construct the sorting order
   * @return a Bson sorting document that can be used in the `sort` method
   *  to sort the database collection of users
   */
  private Bson constructSortingOrder(Context ctx) {
    // Sort the results. Use the `sortby` query param (default "name")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "name");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  /**
   * Sets up routes for the `user` collection endpoints.
   * A UserController instance handles the user endpoints,
   * and the addRoutes method adds the routes to this controller.
   *
   * These endpoints are:
   *   - `GET /api/users/:id`
   *       - Get the specified user
   *   - `GET /api/users?age=NUMBER&company=STRING&name=STRING`
   *      - List users, filtered using query parameters
   *      - `age`, `company`, and `name` are optional query parameters
   *   - `GET /api/usersByCompany`
   *     - Get user names and IDs, possibly filtered, grouped by company
   *   - `DELETE /api/users/:id`
   *      - Delete the specified user
   *   - `POST /api/users`
   *      - Create a new user
   *      - The user info is in the JSON body of the HTTP request
   *
   * GROUPS SHOULD CREATE THEIR OWN CONTROLLERS THAT IMPLEMENT THE
   * `Controller` INTERFACE FOR WHATEVER DATA THEY'RE WORKING WITH.
   * You'll then implement the `addRoutes` method for that controller,
   * which will set up the routes for that data. The `Server#setupRoutes`
   * method will then call `addRoutes` for each controller, which will
   * add the routes for that controller's data.
   *
   * @param server The Javalin server instance
   */
  @Override
  public void addRoutes(Javalin server) {
    // Get the specified todo
    server.get(API_TODOS_BY_ID, this::getTodo);

    // List todos, filtered using query parameters
    server.get(API_TODOS, this::getTodos);

    // List todos, filtered by owner/category
    server.get("/api/todosByOwner", this::getTodosGroupedByOwner);
    server.get("/api/todosByCategory", this::getTodosGroupedByCategory);

    // Add new user with the user info being in the JSON body
    // of the HTTP request
    //server.post(API_TODOS, this::addNewTodo);

    // Delete the specified user
    //server.delete(API_TODOS_BY_ID, this::deleteTodos);
  }
}

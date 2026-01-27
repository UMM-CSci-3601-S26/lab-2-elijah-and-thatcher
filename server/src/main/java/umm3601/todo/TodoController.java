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
  static final String BODY_KEY = "contains";
  static final String CATEGORY_KEY = "category";

  private static final String STATUS_REGEX = "^(complete|incomplete)$";

  private final JacksonMongoCollection<Todo> todosCollection;

  /**
   * Construct a controller for todos.
   *
   * @param database the database containing todo data
   */
  public TodoController(MongoDatabase database) {
    todosCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Set the JSON body of the response to be the single todo
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
   * Set the JSON body of the response to be a list of all the todos returned from the database
   * that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);

    // All three of the find, sort, and into steps happen "in parallel" inside the
    // database system. So MongoDB is going to find the todos with the specified
    // properties, return those sorted in the specified manner, and put the
    // results into an initially empty ArrayList.
    ArrayList<Todo> matchingTodos = todosCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .into(new ArrayList<>());


    // Set the JSON body of the response to be the list of todos returned by the database.
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
   * This checks for the presence of the `owner`, and `category` query
   * parameters and constructs a filter document that will match todos with
   * the specified values for those fields.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *   to filter the database collection of todos
   */
  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters

    // Filter by owner
    if (ctx.queryParamMap().containsKey(OWNER_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(OWNER_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(OWNER_KEY, pattern));
    }

    // Filter by category
    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(CATEGORY_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(CATEGORY_KEY, pattern));
    }

    // Filter by status
    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      String statusParam = ctx.queryParam(STATUS_KEY);
      Boolean statusFilter = null;
      if ("complete".equals(statusParam)) {
        statusFilter = true;
      } else if ("incomplete".equals(statusParam)) {
        statusFilter = false;
      } else {
        throw new BadRequestResponse("The status filter must be either 'complete' or 'incomplete'");
      }
      filters.add(eq(STATUS_KEY, statusFilter));
    }

    // Filter by body
    if (ctx.queryParamMap().containsKey(BODY_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(BODY_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex("body", pattern));
    }

    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }

     /**

   * @param ctx a Javalin HTTP context, which contains the query parameters
   *   used to construct the sorting order
   * @return a Bson sorting document that can be used in the `sort` method
   *  to sort the database collection of todos
   */
  private Bson constructSortingOrder(Context ctx) {
    // Sort the results. Use the `sortby` query param (default "category")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "category");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  // Group todos by owner
  public void getTodosGroupedByOwner(Context ctx) {
    // We'll support sorting the results either by owner name (in either `asc` or `desc` order)
    // or by the number of todos with that owner name (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "_id");
    if (sortBy.equals("owner")) {
      sortBy = "_id";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);


    ArrayList<TodoByOwner> matchingTodos = todosCollection
      // The following aggregation pipeline groups todos by owner, and
      // then counts the number of todos with each owner. It also collects
      // the todo owners and IDs for each todo with each owner name.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id and owner fields
          new Document("$project", new Document("_id", 1).append("owner", 1)),
          // Group the todos by owner, and count the number of todos with each owner
          new Document("$group", new Document("_id", "$owner")
            // Count the number of todos with each owner
            .append("count", new Document("$sum", 1))
            // Collect the todo owners and IDs for each todo with each owner
            .append("todos", new Document("$push", new Document("_id", "$_id").append("owner", "$owner")))),
          // Sort the results. Use the `sortby` query param (default "category")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to TodoGroupResult objects
        TodoByOwner.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }

  // Group todos by category
  public void getTodosGroupedByCategory(Context ctx) {
    // We'll support sorting the results either by category (in either `asc` or `desc` order)
    // or by the number of todos in the category (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "_id");
    if (sortBy.equals("category")) {
      sortBy = "_id";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    ArrayList<TodoByCategory> matchingTodos = todosCollection

      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, owner, and category fields
          new Document("$project", new Document("_id", 1).append("owner", 1).append("category", 1)),
          // Group the todos by category, and count the number of todos in each category
          new Document("$group", new Document("_id", "$category")
            // Count the number of todos in each category
            .append("count", new Document("$sum", 1))
            // Collect the todo owners and IDs for each todo in each category
            .append("todos", new Document("$push", new Document("_id", "$_id").append("owner", "$owner")))),
          // Sort the results. Use the `sortby` query param (default "category")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to TodoGroupResult objects
        // (i.e., a list of TodoGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        TodoByCategory.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }

  /**
   * Sets up routes for the `todo` collection endpoints.
   * A TodoController instance handles the todo endpoints,
   * and the addRoutes method adds the routes to this controller.
   *
   * These endpoints are:
   *   - `GET /api/todos/:id`
   *       - Get the specified todo
   *   - `GET /api/todos?age=NUMBER&company=STRING&name=STRING`
   *      - List todos, filtered using query parameters
   *      - `owner`, `category`, and `status` are optional query parameters
   *   - `GET /api/todosByCategory` / `GET /api/todosByOwner`
   *     - Get todo owners and IDs, possibly filtered, grouped by category/owner
   *   - `DELETE /api/todos/:id`
   *      - Delete the specified todo
   *   - `POST /api/todos`
   *      - Create a new todo
   *      - The todo info is in the JSON body of the HTTP request
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

    // Add new todo with the todo info being in the JSON body
    // of the HTTP request
    //server.post(API_TODOS, this::addNewTodo);

    // Delete the specified todo
    //server.delete(API_TODOS_BY_ID, this::deleteTodos);
  }
}

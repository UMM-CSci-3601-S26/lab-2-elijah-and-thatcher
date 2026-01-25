package umm3601.todo;

//import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
//import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
//import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonMappingException;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
//import io.javalin.validation.BodyValidator;
import io.javalin.validation.Validation;
//import io.javalin.validation.ValidationError;
//import io.javalin.validation.ValidationException;
import io.javalin.validation.Validator;

/**
 * Tests the logic of the TodoController
 *
 * @throws IOException
 */
// The tests here include a ton of "magic numbers" (numeric constants).
// It wasn't clear to me that giving all of them names would actually
// help things. The fact that it wasn't obvious what to call some
// of them says a lot. Maybe what this ultimately means is that
// these tests can/should be restructured so the constants (there are
// also a lot of "magic strings" that Checkstyle doesn't actually
// flag as a problem) make more sense.
@SuppressWarnings({ "MagicNumber" })
class TodoControllerSpec {

  // An instance of the controller we're testing that is prepared in
  // `setupEach()`, and then exercised in the various tests below.
  private TodoController todoController;

  // A Mongo object ID that is initialized in `setupEach()` and used
  // in a few of the tests. It isn't used all that often, though,
  // which suggests that maybe we should extract the tests that
  // care about it into their own spec file?
  private ObjectId samsId;

  // The client and database that will be used
  // for all the tests in this spec file.
  private static MongoClient mongoClient;
  private static MongoDatabase db;

  // Used to translate between JSON and POJOs.
  private static JavalinJackson javalinJackson = new JavalinJackson();

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;

  @Captor
  private ArgumentCaptor<Todo> todoCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  /**
   * Sets up (the connection to the) DB once; that connection and DB will
   * then be (re)used for all the tests, and closed in the `teardown()`
   * method. It's somewhat expensive to establish a connection to the
   * database, and there are usually limits to how many connections
   * a database will support at once. Limiting ourselves to a single
   * connection that will be shared across all the tests in this spec
   * file helps both speed things up and reduce the load on the DB
   * engine.
   */
  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    // Reset our mock context and argument captor (declared with Mockito
    // annotations @Mock and @Captor)
    MockitoAnnotations.openMocks(this);

    // Setup database
    MongoCollection<Document> todoDocuments = db.getCollection("todos");
    todoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
        new Document()
            .append("owner", "Chris")
            .append("status", true)
            .append("body", "This is a video games todo")
            .append("category", "video games"));
    testTodos.add(
        new Document()
            .append("owner", "Chris")
            .append("status", true)
            .append("body", "This is another video games todo")
            .append("category", "video games"));
    testTodos.add(
        new Document()
            .append("owner", "Pat")
            .append("status", false)
            .append("body", "This is a homework todo")
            .append("category", "homework"));
    testTodos.add(
        new Document()
            .append("owner", "Jamie")
            .append("status", false)
            .append("body", "This is a software design todo")
            .append("category", "software design"));

    samsId = new ObjectId();
    Document sam = new Document()
        .append("_id", samsId)
        .append("owner", "Sam")
        .append("status", true)
        .append("body", "This is Sam's todo")
        .append("category", "homework");

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(sam);

    todoController = new TodoController(db);
  }

  @Test
  void addsRoutes() {
    Javalin mockServer = mock(Javalin.class);
    todoController.addRoutes(mockServer);
    verify(mockServer, Mockito.atLeast(2)).get(any(), any());
    //verify(mockServer, Mockito.atLeastOnce()).post(any(), any());
    //verify(mockServer, Mockito.atLeastOnce()).delete(any(), any());
  }

  @Test
  void canGetAllTodos() throws IOException {
    // When something asks the (mocked) context for the queryParamMap,
    // it will return an empty map (since there are no query params in
    // this case where we want all todos).
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    // Now, go ahead and ask the todoController to getTodos
    // (which will, indeed, ask the context for its queryParamMap)
    todoController.getTodos(ctx);

    // We are going to capture an argument to a function, and the type of
    // that argument will be of type ArrayList<Todo> (we said so earlier
    // using a Mockito annotation like this):
    // @Captor
    // private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;
    // We only want to declare that captor once and let the annotation
    // help us accomplish reassignment of the value for the captor
    // We reset the values of our annotated declarations using the command
    // `MockitoAnnotations.openMocks(this);` in our @BeforeEach

    // Specifically, we want to pay attention to the ArrayList<Todo> that
    // is passed as input when ctx.json is called --- what is the argument
    // that was passed? We capture it and can refer to it later.
    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Check that the database collection holds the same number of documents
    // as the size of the captured List<Todo>
    assertEquals(
        db.getCollection("todos").countDocuments(),
        todoArrayListCaptor.getValue().size());
  }

  /**
   * Confirm that if we process a request for todos with status true,
   * that all returned todos have that status, and we get the correct
   * number of todos.
   *
   * @throws IOException
   */
  /*
  @Test
  void canGetTodosWithStatusTrue() throws IOException {
    // We'll need both `String` and `Boolean` representations of
    // the target status
    Boolean targetStatus = false;
    String targetStatusString = targetStatus.toString();

    // Create a `Map` for the `queryParams` that will "return" the string
    // "true" if you ask for the value associated with the `STATUS_KEY`.
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {targetStatusString}));
    // When the code being tested calls `ctx.queryParamMap()` return the
    // the `queryParams` map we just built.
    when(ctx.queryParamMap()).thenReturn(queryParams);
    // When the code being tested calls `ctx.queryParam(STATUS_KEY)` return the
    // `targetStatusString`.
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(targetStatusString);

    // Create a validator that confirms that when we ask for the value associated with
    // `STATUS_KEY` _as an boolean_, we get back the integer value true.
    Validation validation = new Validation();
    // The `STATUS_KEY` should be name of the key whose value is being validated.
    Validator<Boolean> validator = validation.validator(TodoController.STATUS_KEY, Boolean.class, targetStatusString);
    // When the code being tested calls `ctx.queryParamAsClass("status", Boolean.class)`
    // we'll return the `Validator` we just constructed.
    when(ctx.queryParamAsClass(TodoController.STATUS_KEY, Boolean.class))
        .thenReturn(validator);

    todoController.getTodos(ctx);

    // Confirm that the code being tested calls `ctx.json(…)`, and capture whatever
    // is passed in as the argument when `ctx.json()` is called.
    verify(ctx).json(todoArrayListCaptor.capture());
    // Confirm that the code under test calls `ctx.status(HttpStatus.OK)` is called.
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we get back two todos.
    assertEquals(1, todoArrayListCaptor.getValue().size());
    // Confirm that both todos have status true.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetStatus, todo.status);
    }
    // Generate a list of the owners of the returned todos.
    List<String> owners = todoArrayListCaptor.getValue().stream().map(todo -> todo.owner).collect(Collectors.toList());
    // Confirm that the returned `owners` contain the two names of the true todos
    assertTrue(owners.contains("Chris"));
  }
  */

  // Make sure we can get todos by owner
  @Test
  void getTodosByOwner() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    String roleString = "Chris";
    queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {roleString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);

    // Create a validator that confirms that when we ask for the value associated with
    // `OWNER_KEY` we get back a string that represents a legal role.
    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.OWNER_KEY, String.class, roleString);
    when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class)).thenReturn(validator);
    when(ctx.queryParam(TodoController.OWNER_KEY)).thenReturn(roleString);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals(2, todoArrayListCaptor.getValue().size());
  }

  // Make sure we can get todos by category
  @Test
  void getTodosByCategory() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    String roleString = "video games";
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {roleString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);

    // Create a validator that confirms that when we ask for the value associated with
    // `CATEGORY_KEY` we get back a string that represents a legal role.
    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.CATEGORY_KEY, String.class, roleString);
    when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class)).thenReturn(validator);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn(roleString);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals(2, todoArrayListCaptor.getValue().size());
  }

  // -- TESTS FOR IDs -- \\
  // Make sure we can get a todo by its ID
  @Test
  void getTodoWithExistentId() throws IOException {
    String id = samsId.toHexString();
    when(ctx.pathParam("id")).thenReturn(id);

    todoController.getTodo(ctx);

    verify(ctx).json(todoCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals("Sam", todoCaptor.getValue().owner);
    assertEquals(samsId.toHexString(), todoCaptor.getValue()._id);
  }

  // Make sure the correct error is thrown for invalid IDs
  @Test
  void getTodoWithBadId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested todo id wasn't a legal Mongo Object ID.", exception.getMessage());
  }

  // Make sure the correct error is thrown for non-existent IDs
  @Test
  void getTodoWithNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested todo was not found", exception.getMessage());
  }


  // -- TESTS FOR OWNER -- \\
  @Captor
  private ArgumentCaptor<ArrayList<TodoByOwner>> todoByOwnerListCaptor;

  @Test
  void testGetTodosGroupedByOwner() {
    when(ctx.queryParam("sortBy")).thenReturn("owner");
    when(ctx.queryParam("sortOrder")).thenReturn("asc");
    todoController.getTodosGroupedByOwner(ctx);

    // Capture the argument to `ctx.json()`
    verify(ctx).json(todoByOwnerListCaptor.capture());

    // Get the value that was passed to `ctx.json()`
    ArrayList<TodoByOwner> result = todoByOwnerListCaptor.getValue();

    // There are 4 owners in the test data, so we should have 4 entries in the
    // result.
    assertEquals(4, result.size());

    // The owners should be in alphabetical order by owner name,
    // with counts of 2, 1, 1, and 1 respectively
    TodoByOwner chris = result.get(0);
    assertEquals("Chris", chris._id);
    assertEquals(2, chris.count);
    TodoByOwner jamie = result.get(1);
    assertEquals("Jamie", jamie._id);
    assertEquals(1, jamie.count);
    TodoByOwner pat = result.get(2);
    assertEquals("Pat", pat._id);
    assertEquals(1, pat.count);
    TodoByOwner sam = result.get(3);
    assertEquals("Sam", sam._id);
    assertEquals(1, sam.count);
  }

  @Test
  void testGetTodosGroupedByOwnerDescending() {
    when(ctx.queryParam("sortBy")).thenReturn("owner");
    when(ctx.queryParam("sortOrder")).thenReturn("desc");
    todoController.getTodosGroupedByOwner(ctx);

    // Capture the argument to `ctx.json()`
    verify(ctx).json(todoByOwnerListCaptor.capture());

    // Get the value that was passed to `ctx.json()`
    ArrayList<TodoByOwner> result = todoByOwnerListCaptor.getValue();

    // There are 4 owners in the test data, so we should have 4 entries in the
    // result.
    assertEquals(4, result.size());

    // The owners should be in alphabetical order by owner name,
    // with counts of 2, 1, 1, and 1 respectively
    TodoByOwner chris = result.get(3);
    assertEquals("Chris", chris._id);
    assertEquals(2, chris.count);
    TodoByOwner jamie = result.get(2);
    assertEquals("Jamie", jamie._id);
    assertEquals(1, jamie.count);
    TodoByOwner pat = result.get(1);
    assertEquals("Pat", pat._id);
    assertEquals(1, pat.count);
    TodoByOwner sam = result.get(0);
    assertEquals("Sam", sam._id);
    assertEquals(1, sam.count);
  }

  @Test
  void testGetTodosGroupedByOwnerOrderedByCount() {
    when(ctx.queryParam("sortBy")).thenReturn("count");
    when(ctx.queryParam("sortOrder")).thenReturn("asc");
    todoController.getTodosGroupedByOwner(ctx);

    // Capture the argument to `ctx.json()`
    verify(ctx).json(todoByOwnerListCaptor.capture());

    // Get the value that was passed to `ctx.json()`
    ArrayList<TodoByOwner> result = todoByOwnerListCaptor.getValue();

    // There are 4 owners in the test data, so we should have 4 entries in the
    // result.
    assertEquals(4, result.size());

    // The owner should be in order by todo count, each with 1 count (excluding chris),
    // respectively. We don't know which order "Pat", "Jamie", and "sam" will be in, since
    // they
    // all have a count of 1. So we'll get all three and then swap them if
    // necessary.
    TodoByOwner pat = result.get(0);
    TodoByOwner jamie = result.get(1);
    TodoByOwner sam = result.get(2);
    if (pat._id.equals("Jamie")) {
      jamie = result.get(0);
      pat = result.get(1);
    } else if (pat._id.equals("Sam")) {
      sam = result.get(0);
      pat = result.get(1);
    } else if (jamie._id.equals("Sam")) {
      sam = result.get(1);
      jamie = result.get(2);
    }
    TodoByOwner chris = result.get(3);
    assertEquals("Pat", pat._id);
    assertEquals(1, pat.count);
    assertEquals("Jamie", jamie._id);
    assertEquals(1, jamie.count);
    assertEquals("Sam", sam._id);
    assertEquals(1, sam.count);
    assertEquals("Chris", chris._id);
    assertEquals(2, chris.count);
  }

  // -- TESTS FOR CATEGORY -- \\
  @Captor
  private ArgumentCaptor<ArrayList<TodoByCategory>> todoByCategoryListCaptor;

  @Test
  void testGetTodosGroupedByCategory() {
    when(ctx.queryParam("sortBy")).thenReturn("category");
    when(ctx.queryParam("sortOrder")).thenReturn("asc");
    todoController.getTodosGroupedByCategory(ctx);

    // Capture the argument to `ctx.json()`
    verify(ctx).json(todoByCategoryListCaptor.capture());

    // Get the value that was passed to `ctx.json()`
    ArrayList<TodoByCategory> result = todoByCategoryListCaptor.getValue();

    // There are 3 unique categories in the test data, so we should have 3 entries in the
    // result.
    assertEquals(3, result.size());

    // The categories should be in alphabetical order by category name,
    // each with two entires except "software design" which has 1
    TodoByCategory homework = result.get(0);
    assertEquals("homework", homework._id);
    assertEquals(2, homework.count);
    TodoByCategory softwareDesign = result.get(1);
    assertEquals("software design", softwareDesign._id);
    assertEquals(1, softwareDesign.count);
    TodoByCategory videoGames = result.get(2);
    assertEquals("video games", videoGames._id);
    assertEquals(2, videoGames.count);
  }

  @Test
  void testGetTodosGroupedByCategoryDescending() {
    when(ctx.queryParam("sortBy")).thenReturn("category");
    when(ctx.queryParam("sortOrder")).thenReturn("desc");
    todoController.getTodosGroupedByCategory(ctx);

    // Capture the argument to `ctx.json()`
    verify(ctx).json(todoByCategoryListCaptor.capture());

    // Get the value that was passed to `ctx.json()`
    ArrayList<TodoByCategory> result = todoByCategoryListCaptor.getValue();

    // There are 3 unique categories in the test data, so we should have 3 entries in the
    // result.
    assertEquals(3, result.size());

    // The categories should be in reverse-alphabetical order by category name,
    // each with two entires except "software design" which has 1
    TodoByCategory homework = result.get(2);
    assertEquals("homework", homework._id);
    assertEquals(2, homework.count);
    TodoByCategory softwareDesign = result.get(1);
    assertEquals("software design", softwareDesign._id);
    assertEquals(1, softwareDesign.count);
    TodoByCategory videoGames = result.get(0);
    assertEquals("video games", videoGames._id);
    assertEquals(2, videoGames.count);
  }

  @Test
  void testGetTodosGroupedByCategoryOrderedByCount() {
    when(ctx.queryParam("sortBy")).thenReturn("count");
    when(ctx.queryParam("sortOrder")).thenReturn("asc");
    todoController.getTodosGroupedByCategory(ctx);

    // Capture the argument to `ctx.json()`
    verify(ctx).json(todoByCategoryListCaptor.capture());

    // Get the value that was passed to `ctx.json()`
    ArrayList<TodoByCategory> result = todoByCategoryListCaptor.getValue();

    // There are 3 unique categories in the test data, so we should have 3 entries in the
    // result.
    assertEquals(3, result.size());

    // The category should be in order by todo count, each with 2 counts for homework and video games
    // We don't know which order "homework" and "video games" will be in, since
    // they both have a count of 2. So we'll get them both and then swap if necessary.
    TodoByCategory softwareDesign = result.get(0);
    TodoByCategory homework = result.get(1);
    TodoByCategory videoGames = result.get(2);
    // Swap if necessary
    if (homework._id.equals("video games")) {
      videoGames = result.get(1);
      homework = result.get(2);
    }
    assertEquals("software design", softwareDesign._id);
    assertEquals(1, softwareDesign.count);
    assertEquals("homework", homework._id);
    assertEquals(2, homework.count);
    assertEquals("video games", videoGames._id);
    assertEquals(2, videoGames.count);
  }

  /*
  @Test
  void addTodo() throws IOException {
    // Create a new todo to add
    Todo newTodo = new Todo();
    newTodo.name = "Test Todo";
    newTodo.age = 25;
    newTodo.company = "testers";
    newTodo.email = "test@example.com";
    newTodo.role = "viewer";

    // Use `javalinJackson` to convert the `Todo` object to a JSON string representing that todo.
    // This would be equivalent to:
    //   String testNewTodo = """
    //       {
    //         "name": "Test Todo",
    //         "age": 25,
    //         "company": "testers",
    //         "email": "test@example.com",
    //         "role": "viewer"
    //       }
    //       """;
    // but using `javalinJackson` to generate the JSON avoids repeating all the field values,
    // which is then less error prone.
    String newTodoJson = javalinJackson.toJsonString(newTodo, Todo.class);

    // A `BodyValidator` needs
    //   - The string (`newTodoJson`) being validated
    //   - The class (`Todo.class) it's trying to generate from that string
    //   - A function (`() -> Todo`) which "shows" the validator how to convert
    //     the JSON string to a `Todo` object. We'll again use `javalinJackson`,
    //     but in the other direction.
    when(ctx.bodyValidator(Todo.class))
      .thenReturn(new BodyValidator<Todo>(newTodoJson, Todo.class,
                    () -> javalinJackson.fromJsonString(newTodoJson, Todo.class)));

    todoController.addNewTodo(ctx);
    verify(ctx).json(mapCaptor.capture());

    // Our status should be 201, i.e., our new todo was successfully created.
    verify(ctx).status(HttpStatus.CREATED);

    // Verify that the todo was added to the database with the correct ID
    Document addedTodo = db.getCollection("todos")
        .find(eq("_id", new ObjectId(mapCaptor.getValue().get("id")))).first();

    // Successfully adding the todo should return the newly generated, non-empty
    // MongoDB ID for that todo.
    assertNotEquals("", addedTodo.get("_id"));
    // The new todo in the database (`addedTodo`) should have the same
    // field values as the todo we asked it to add (`newTodo`).
    assertEquals(newTodo.name, addedTodo.get("name"));
    assertEquals(newTodo.age, addedTodo.get(TodoController.AGE_KEY));
    assertEquals(newTodo.company, addedTodo.get(TodoController.COMPANY_KEY));
    assertEquals(newTodo.email, addedTodo.get("email"));
    assertEquals(newTodo.role, addedTodo.get(TodoController.ROLE_KEY));
    assertNotNull(addedTodo.get("avatar"));
  }

  @Test
  void addInvalidEmailTodo() throws IOException {
    // Create a new todo JSON string to add.
    // Note that it has an invalid string for the email address, which is
    // why we're using a `String` here instead of a `Todo` object
    // like we did in the previous tests.
    String newTodoJson = """
      {
        "name": "Test Todo",
        "age": 25,
        "company": "testers",
        "email": "invalidemail",
        "role": "viewer"
      }
      """;

    when(ctx.body()).thenReturn(newTodoJson);
    when(ctx.bodyValidator(Todo.class))
      .thenReturn(new BodyValidator<Todo>(newTodoJson, Todo.class,
                    () -> javalinJackson.fromJsonString(newTodoJson, Todo.class)));

    // This should now throw a `ValidationException` because
    // the JSON for our new todo has an invalid email address.
    ValidationException exception = assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });

    // This `ValidationException` was caused by a custom check, so we just get the message from the first
    // error (which is a `"REQUEST_BODY"` error) and convert that to a string with `toString()`. This gives
    // a `String` that has all the details of the exception, which we can make sure contains information
    // that would help a developer sort out validation errors.
    String exceptionMessage = exception.getErrors().get("REQUEST_BODY").get(0).toString();

    // The message should be the message from our code under test, which should also include the text
    // we tried to parse as an email, namely "invalidemail".
    assertTrue(exceptionMessage.contains("invalidemail"));
  }

  @Test
  void addTodoWithoutName() throws IOException {
    String newTodoJson = """
        {
          "age": 25,
          "company": "testers",
          "email": "test@example.com",
          "role": "viewer"
        }
        """;

    when(ctx.body()).thenReturn(newTodoJson);
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(newTodoJson, Todo.class,
                        () -> javalinJackson.fromJsonString(newTodoJson, Todo.class)));

    // This should now throw a `ValidationException` because
    // the JSON for our new todo has no name.
    ValidationException exception = assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
    // This `ValidationException` was caused by a custom check, so we just get the message from the first
    // error (which is a `"REQUEST_BODY"` error) and convert that to a string with `toString()`. This gives
    // a `String` that has all the details of the exception, which we can make sure contains information
    // that would help a developer sort out validation errors.
    String exceptionMessage = exception.getErrors().get("REQUEST_BODY").get(0).toString();

    // The message should be the message from our code under test, which should also include some text
    // indicating that there was a missing todo name.
    assertTrue(exceptionMessage.contains("non-empty todo name"));
  }

  @Test
  void addEmptyNameTodo() throws IOException {
    String newTodoJson = """
        {
          "name": "",
          "age": 25,
          "company": "testers",
          "email": "test@example.com",
          "role": "viewer"
        }
        """;

    when(ctx.body()).thenReturn(newTodoJson);
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(newTodoJson, Todo.class,
                        () -> javalinJackson.fromJsonString(newTodoJson, Todo.class)));

    // This should now throw a `ValidationException` because
    // the JSON for our new todo has an invalid email address.
    ValidationException exception = assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
    // This `ValidationException` was caused by a custom check, so we just get the message from the first
    // error (which is a `"REQUEST_BODY"` error) and convert that to a string with `toString()`. This gives
    // a `String` that has all the details of the exception, which we can make sure contains information
    // that would help a developer sort out validation errors.
    String exceptionMessage = exception.getErrors().get("REQUEST_BODY").get(0).toString();

    // The message should be the message from our code under test, which should also include some text
    // indicating that there was an empty string for the todo name.
    assertTrue(exceptionMessage.contains("non-empty todo name"));
  }

  @Test
  void addInvalidRoleTodo() throws IOException {
    String newTodoJson = """
        {
          "name": "Test Todo",
          "age": 25,
          "company": "testers",
          "email": "test@example.com",
          "role": "invalidrole"
        }
        """;

    when(ctx.body()).thenReturn(newTodoJson);
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(newTodoJson, Todo.class,
                        () -> javalinJackson.fromJsonString(newTodoJson, Todo.class)));

    // This should now throw a `ValidationException` because
    // the JSON for our new todo has an invalid todo role.
    ValidationException exception = assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
    // This `ValidationException` was caused by a custom check, so we just get the message from the first
    // error (which is a `"REQUEST_BODY"` error) and convert that to a string with `toString()`. This gives
    // a `String` that has all the details of the exception, which we can make sure contains information
    // that would help a developer sort out validation errors.
    String exceptionMessage = exception.getErrors().get("REQUEST_BODY").get(0).toString();

    // The message should be the message from our code under test, which should also include the text
    // we tried to use as a role, namely "invalidrole".
    assertTrue(exceptionMessage.contains("invalidrole"));
  }

  @Test
  void addTodoWithoutCompany() throws IOException {
    String newTodoJson = """
        {
          "name": "Test Todo",
          "age": 25,
          "email": "test@example.com",
          "role": "viewer"
        }
        """;

    when(ctx.body()).thenReturn(newTodoJson);
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(newTodoJson, Todo.class,
                        () -> javalinJackson.fromJsonString(newTodoJson, Todo.class)));

    // This should now throw a `ValidationException` because
    // the JSON for our new todo has no company.
    ValidationException exception = assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
    // This `ValidationException` was caused by a custom check, so we just get the message from the first
    // error (which is a `"REQUEST_BODY"` error) and convert that to a string with `toString()`. This gives
    // a `String` that has all the details of the exception, which we can make sure contains information
    // that would help a developer sort out validation errors.
    String exceptionMessage = exception.getErrors().get("REQUEST_BODY").get(0).toString();

    // The message should be the message from our code under test, which should also include some text
    // indicating that there was a missing company name.
    assertTrue(exceptionMessage.contains("non-empty company name"));
  }

  @Test
  void addTodoWithNeitherCompanyNorName() throws IOException {
    String newTodoJson = """
        {
          "name": "",
          "age": 25,
          "company": "",
          "email": "test@example.com",
          "role": "viewer"
        }
        """;

    when(ctx.body()).thenReturn(newTodoJson);
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(newTodoJson, Todo.class,
                        () -> javalinJackson.fromJsonString(newTodoJson, Todo.class)));

    // This should now throw a `ValidationException` because
    // the JSON for our new todo has an invalid email address.
    ValidationException exception = assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
    // We should have _two_ errors here both of type `REQUEST_BODY`. The first should be for the
    // missing name and the second for the missing company.
    List<ValidationError<Object>> errors = exception.getErrors().get("REQUEST_BODY");

    // Check the todo name error
    // It's a little fragile to have the tests assume the todo error is first and the
    // company error is second.
    String nameExceptionMessage = errors.get(0).toString();
    assertTrue(nameExceptionMessage.contains("non-empty todo name"));

    // Check the company name error
    String companyExceptionMessage = errors.get(1).toString();
    assertTrue(companyExceptionMessage.contains("non-empty company name"));
  }

  @Test
  void deleteFoundTodo() throws IOException {
    String testID = samsId.toHexString();
    when(ctx.pathParam("id")).thenReturn(testID);

    // Todo exists before deletion
    assertEquals(1, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));

    todoController.deleteTodo(ctx);

    verify(ctx).status(HttpStatus.OK);

    // Todo is no longer in the database
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));
  }

  @Test
  void tryToDeleteNotFoundTodo() throws IOException {
    String testID = samsId.toHexString();
    when(ctx.pathParam("id")).thenReturn(testID);

    todoController.deleteTodo(ctx);
    // Todo is no longer in the database
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));

    assertThrows(NotFoundResponse.class, () -> {
      todoController.deleteTodo(ctx);
    });

    verify(ctx).status(HttpStatus.NOT_FOUND);

    // Todo is still not in the database
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));
  }
*/
}

package com.example.collabcode.controller;

import com.example.collabcode.dto.RoleAssignmentRequest;
import com.example.collabcode.model.*;
import com.example.collabcode.repository.*;
import com.example.collabcode.service.Impl.CodeExecutorImpl;
import com.example.collabcode.service.RoomService;
import com.example.collabcode.service.UserService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api")
public class RoomController {
    @Autowired
    private VersionRepository versionRepository;
    @Autowired
    private CodeRepository codeRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    FileRepository fileRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private CodeExecutorImpl codeExecutor;
    @Autowired
    private UserService userService;
    @Autowired
    private  ExecResultRepository execResultRepository;
    @Autowired
    private RoomService roomService;
    private final Lock fileLock = new ReentrantLock();  // A lock to manage file editing concurrency
    private CookieCsrfTokenRepository jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody User user) {
        try {
            log.info("Registering user: {}", user); // Log user object

            String result = userService.registerUser(user);

            if ("User registered successfully".equals(result)) {
                List<GrantedAuthority> authorities = new ArrayList<>();
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        user.getUsername(), user.getPassword(), authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                return ResponseEntity.status(HttpStatus.OK).body(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
        } catch (Exception e) {
            log.error("Error during registration: ", e); // Log the exception
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error during registration: " + e.getMessage());
        }
    }
    /*@RequestMapping(value = "/login", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();  // Allow OPTIONS request to proceed
    }*/
//    @PostMapping(value = "/login")
//    public ResponseEntity<User> loginUser(@RequestBody User user) {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        System.out.println("authentication "+authentication);
//        // Check if the user is already authenticated (i.e., using OAuth2)
//        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
//            // Optionally, you can return a response indicating that the user is already authenticated
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null); // Or a message saying already logged in
//        }
//        System.out.println("Authentication: "+authentication.isAuthenticated());
//
//        // Proceed with traditional username/password login
//        System.out.println("Attempting login for user: " + user.getUsername()+"and pass: "+user.getPassword());
//        User loggedInUser = userService.loginUser(user.getUsername(), user.getPassword());
//        System.out.println("Logged in user: " + loggedInUser); // Add this for debugging
//
//        if (loggedInUser != null) {
//            // Set authentication in the security context with roles/authorities
//            System.out.println("User logged in: " + loggedInUser.getUsername());
//            List<GrantedAuthority> authorities = new ArrayList<>();
//            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
////            Authentication auth = new UsernamePasswordAuthenticationToken(
////                    loggedInUser.getUsername(), user.getPassword(), authorities);
//            Authentication auth = new UsernamePasswordAuthenticationToken(
//                    loggedInUser, null, authorities);
//            SecurityContextHolder.getContext().setAuthentication(auth);
//            SecurityContextHolder.getContext().setAuthentication(auth);
//            System.out.println("Authenticated user: " + loggedInUser.getUsername());
//
//            return ResponseEntity.status(HttpStatus.OK).body(loggedInUser);
//        } else {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
//        }
//    }
    @PostMapping("/login")
    public ResponseEntity<User> loginUser(@RequestBody User user) {
        User loggedInUser = userService.loginUser(user.getUsername(), user.getPassword());

        if (loggedInUser != null) {

            // Set authentication in the security context with roles/authorities
            List<GrantedAuthority> authorities = new ArrayList<>();
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    loggedInUser.getUsername(), user.getPassword(), authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            System.out.println("Authenticated user: " + loggedInUser.getUsername());

            return ResponseEntity.status(HttpStatus.OK).body(loggedInUser);
        }
        else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }
    @GetMapping("/rooms")
    public ResponseEntity<Map<String, List<Room>>> getRoomsForUser(@RequestParam String username) {
        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            // Create a set to hold owned rooms and a list for participant rooms
            Set<Room> ownedRooms = new HashSet<>(roomRepository.findByOwner(user.getUsername()));
            List<Room> participantRooms = roomRepository.findByParticipants(user.getUserId());

            // Remove any owned rooms from the participant list
            participantRooms.removeAll(ownedRooms);

            // Prepare the response structure
            Map<String, List<Room>> response = new HashMap<>();
            response.put("ownedRooms", new ArrayList<>(ownedRooms));
            response.put("participantRooms", participantRooms);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching rooms for user: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @PostMapping("/rooms/{roomId}/files/{fileId}/comments")
    public ResponseEntity<Comment> addComment(@PathVariable String roomId,
                                              @PathVariable String fileId,
                                              @RequestBody Comment comment,
                                              @RequestParam String username) {
        // Log the incoming comment for debugging
        log.info("Adding comment: {}, for Room ID: {}, File ID: {}, User: {}", comment, roomId, fileId, username);

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        // Get the user's role from the room's userRoles map
        String role = room.getUserRoles().get(username);
        System.out.println(role);
        if (role == null || role.equals("VIEWER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        // Set additional properties if needed
        comment.setFileId(fileId);
        comment.setRoomId(roomId);

        // Validate comment fields (you can customize this further)
        if (comment.getContent() == null || comment.getContent().isEmpty()) {
            return ResponseEntity.badRequest().body(null); // Return 400 if content is missing
        }

        // Save the comment
        commentRepository.save(comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }


    @GetMapping("/rooms/{roomId}/files/{fileId}/comments")
    public ResponseEntity<List<Comment>> getComments(@PathVariable String roomId,
                                                     @PathVariable String fileId) {
        List<Comment> comments = commentRepository.findByRoomIdAndFileId(roomId, fileId);
        return ResponseEntity.ok(comments);
    }

    @DeleteMapping("/rooms/{roomId}/files/{fileId}/comments/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable String roomId,
                                                @PathVariable String fileId,
                                                @PathVariable String commentId,
                                                @RequestParam String username) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found");
        }

        // Get the user's role from the room's userRoles map
        String role = room.getUserRoles().get(username);
        if (role == null || role.equals("VIEWER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to delete comments");
        }

        Optional<Comment> comment = commentRepository.findById(commentId);
        if (!comment.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
        }

        try {
            commentRepository.deleteById(commentId);
            return ResponseEntity.ok("Comment deleted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting comment: " + e.getMessage());
        }
    }

    @GetMapping("/rooms/{roomId}/user-role")
    public ResponseEntity<Map<String, String>> getUserRoleInRoom(@PathVariable String roomId, @RequestParam String username) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        String role = room.getUserRoles().get(username);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        Map<String, String> response = new HashMap<>();
        response.put("role", role);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rooms/{roomId}/files/create")
    public ResponseEntity<Map<String, Object>> createNewFile(
            @PathVariable String roomId,
            @RequestBody File newFile, // Only expect the file name
            @RequestParam String username) {

        Map<String, Object> response = new HashMap<>();

        // Check if the room exists
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            response.put("message", "Room not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Check the user's role in the room
        String role = room.getUserRoles().get(username);
        if (role == null || !"ADMIN".equals(role)) {
            response.put("message", "You do not have permission to create a file.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Generate a unique ID for the new file (e.g., using UUID)
        String fileId = UUID.randomUUID().toString();
        newFile.setId(fileId); // Set the generated ID
        newFile.setOwner(username);
        newFile.setRoomId(roomId);
        newFile.setLastModified(System.currentTimeMillis());

        // Set default language if not provided
        if (newFile.getLang() == null || newFile.getLang().isEmpty()) {
            newFile.setLang("python"); // Set default language
        }

        // Set default content if not provided
        if (newFile.getContent() == null) {
            newFile.setContent(""); // Set default content as empty
        }

        // Save the new file to the repository
        File savedFile = fileRepository.save(newFile);
        room.getFileIds().add(savedFile.getId());
        roomRepository.save(room);
        response.put("file", savedFile);
        return ResponseEntity.ok(response);
    }

    private final String baseDirectory = "uploads"; // Define base directory for storing files

    @PutMapping("/rooms/{roomId}/files/{fileId}")
    public ResponseEntity<String> editFile(@PathVariable String roomId, @PathVariable String fileId,
                                           @RequestBody MultipartFile newFile, @RequestParam String username) throws IOException {
        // Find the room and check if it exists
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found.");
        }

        // Check if the user has permission to edit the file
        String role = room.getUserRoles().get(username);
        if (!"EDITOR".equals(role) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to edit this file.");
        }

        // Lock the file to ensure safe concurrent editing
        fileLock.lock();
        try {
            // Fetch the file from the database
            File file = fileRepository.findById(fileId).orElse(null);
            if (file == null || !file.getRoomId().equals(roomId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found.");
            }

            // Save a new version of the file
            Version version = new Version();
            version.setFileId(file.getId());
            version.setCode(new String(newFile.getBytes())); // New content
            version.setTimestamp(System.currentTimeMillis());
            version.setAuthor(username);
            versionRepository.save(version);

            // Update the file content and metadata
            file.setContent(new String(newFile.getBytes())); // Update file content
            file.setLastModified(System.currentTimeMillis()); // Update last modified timestamp
            fileRepository.save(file); // Save the updated file in the DB

            // Optionally, overwrite the physical file on the server
            Path filePath = Paths.get(baseDirectory, roomId, file.getName());
            Files.copy(newFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok("File edited and version updated successfully.");
        } finally {
            fileLock.unlock();
        }
    }

    @GetMapping("/rooms/{roomId}/files/{fileId}")
    public ResponseEntity<Map<String, Object>> getFileContent(@PathVariable String roomId,
                                                              @PathVariable String fileId, @RequestParam String username) {
        log.info("Fetching content for Room ID: {}, File ID: {}, User: {}", roomId, fileId, username);

        Room room = roomRepository.findById(roomId).orElse(null);
        System.out.println("room ; "+room);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        // Get the user's role from the room's userRoles map
        String role = room.getUserRoles().get(username);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        File file = fileRepository.findById(fileId).orElse(null);
        System.out.println("filename; "+file.getName());
        if (file == null || !file.getRoomId().equals(roomId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        // Prepare the response
        Map<String, Object> response = new HashMap<>();
        response.put("file", file);
        response.put("role", role);  // Return the user's role with the file content
        return ResponseEntity.ok(response);
    }
    @PostMapping(value = "/createRoom", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Object> createRoom(@RequestBody Room room) {
        // Check if room name and UUID are provided
        if (room.getName() == null || room.getUuid() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Room name and UUID are required.");
        }

        User user = userRepository.findById(room.getUuid()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User not found");
        }

        // Check if the user already has a room with the same name
        if (user.getRoomIds().contains(room.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("You already have a room with this id.");
        }
        // Set the owner of the room
        room.setOwner(user.getUsername());

        // Initialize userRoles if null
        if (room.getUserRoles() == null) {
            room.setUserRoles(new HashMap<>());
        }

        // Set user role
        room.getUserRoles().put(user.getUsername(), "ADMIN");

        // Add participant correctly
        if (room.getParticipants() == null) {
            room.setParticipants(new ArrayList<>()); // Ensure participants is initialized
        }
        room.getParticipants().add(user.getUserId()); // Add user ID directly

        // Save room to repository
        roomRepository.save(room);

        // Update user's roomIds
        user.getRoomIds().add(room.getId());
        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.OK).body("Room Created!");
    }

    @GetMapping(value = "/joinRoom", produces = "application/json")
    public ResponseEntity<Object> joinRoom(@RequestParam String uuid, @RequestParam String username) {
        List<Room> roomOptional = roomRepository.findByUuid(uuid);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid room UUID.");
        }
  // Loop through the rooms and check if the user is either the owner or a participant
        Room room = null;
        for (Room r : roomOptional) {
            if (r.getOwner().equals(username) || r.getParticipants().contains(username)) {
                room = r;
                break;
            }
        }

        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not part of this room.");
        }
        // Return the room details and user role
        Map<String, Object> response = new HashMap<>();
        response.put("room", room);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/rooms/{roomId}/files")
    public ResponseEntity<List<File>> getFilesInRoom(@PathVariable String roomId) {
        log.info("Fetching files for room ID: {}", roomId);

        // Fetch the room by room ID
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            log.error("Room not found for ID: {}", roomId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        log.info("Found room: {}", room);
        // Find the user associated with the room
        Optional<User> userOptional = userRepository.findByUsername(room.getOwner());
        if (userOptional.isEmpty()) {
            log.error("User not found for room owner: {}", room.getOwner());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        User user = userOptional.get();

        log.info("Found user: {}", user);

        // Check if the user is a participant in the room
        if (!room.getUserRoles().containsKey(user.getUsername())) {
            log.error("User {} does not have access to room ID: {}", user.getUsername(), roomId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        // Fetch the files in the room
        List<File> files = fileRepository.findByRoomId(roomId);
        log.info("Files found for room ID {}: {}", roomId, files);

        if (files.isEmpty()) {
            log.warn("No files found for room ID: {}", roomId);
        }

        return ResponseEntity.ok(files);
    }

    @PostMapping("/rooms/{roomId}/files/{fileId}/exec")
    public ResponseEntity<String> executeFile(@PathVariable String roomId, @PathVariable String fileId,
                                              @RequestBody Map<String, Object> requestData) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found.");
        }
        String username = (String) requestData.get("username");
        String language = (String) requestData.get("language"); // Get the language from the request body
        String role = room.getUserRoles().get(username);

        if (!"EDITOR".equals(role) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to execute this file.");
        }
        File file = fileRepository.findById(fileId).orElse(null);
        if (file == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found.");
        }
        if (file.getContent() == null || file.getLang() == null) {
            return ResponseEntity.badRequest().body("File content or language is not defined.");
        }
        if (!file.getLang().equalsIgnoreCase(language)) {
            return ResponseEntity.badRequest().body("Language mismatch. Please ensure the language is correct.");
        }
        Code code = new Code();
        code.setCode(file.getContent());
        code.setLang(language);
        ExecResult result = codeExecutor.codeExecutor(code);
        // Save the execution result in the database
        ExecResult execResult = new ExecResult(result.getOut(), result.getTte(),file.getName());
        execResultRepository.save(execResult); // Save to MongoDB
        // Return the execution output
        return ResponseEntity.ok(execResult.getOut());
    }

    @PostMapping("/rooms/{roomId}/files/{fileId}/saveAndVersion")
    public ResponseEntity<String> saveFileAndVersion(@PathVariable String roomId,
                                                     @PathVariable String fileId,
                                                     @RequestBody Map<String, String> requestBody) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found.");
        }
        String username = requestBody.get("username");
        String role = room.getUserRoles().get(username);

        if (!"EDITOR".equals(role) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to save this file.");
        }
        // Extracting parameters from the requestBody
        String code = requestBody.get("code");
        String lang = requestBody.get("lang");
        String author = requestBody.get("author"); // Assuming you include author in requestBody

        // Fetch the file by fileId
        File file = fileRepository.findById(fileId).orElse(null);
        if (file == null) {
            return ResponseEntity.notFound().build(); // File not found
        }
        // Update the file's content and last modified timestamp
        file.setContent(code);
        file.setLang(lang);
        file.setLastModified(Instant.now().toEpochMilli()); // Update last modified timestamp

        // Save the updated file
        fileRepository.save(file);

        // Create a new version object
        Version version = new Version();
        version.setFileId(fileId);
        version.setRoomId(roomId);
        version.setCode(code);
        version.setTimestamp(Instant.now().toEpochMilli()); // Current timestamp
        version.setAuthor(author);

        // Save the version in the versions collection
        versionRepository.save(version);

        return ResponseEntity.ok("File and version saved successfully.");
    }
    @PostMapping("/rooms/{roomId}/files/{fileId}/revert/{versionId}")
    public ResponseEntity<Version> revertVersion(@PathVariable String roomId,
                                                 @PathVariable String fileId,
                                                 @PathVariable String versionId) {

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // Room not found, return 404
        }

        User user = userRepository.findById(room.getUuid()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // User not found, return 404
        }

        String role = room.getUserRoles().get(user.getUsername());
        if (!"EDITOR".equals(role) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // No permission, return 403
        }
        // Logic to revert the version
        Version revertedVersion = versionRepository.findById(versionId).orElse(null);
        if (revertedVersion == null) {
            return ResponseEntity.notFound().build();
        }
        // Update the file content to the reverted version's content
        File file = fileRepository.findById(fileId).orElse(null);
        if (file != null) {
            file.setContent(revertedVersion.getCode());
            fileRepository.save(file);
        }
        return ResponseEntity.ok(revertedVersion); // Return the reverted version
    }

    // Get all versions of a file
    @GetMapping("/rooms/{roomId}/files/{fileId}/versions")
    public ResponseEntity<List<Version>> getFileVersions(@PathVariable String roomId,
                                                         @PathVariable String fileId) {
        System.out.println("Fetching versions for fileId: " + fileId); // Log fileId

        List<Version> versions = versionRepository.findByRoomIdAndFileId(roomId, fileId);
        return ResponseEntity.ok(versions);
    }

    public String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Authenticated user: " + authentication);
        if (authentication != null) {
            System.out.println("Is Authenticated: " + authentication.isAuthenticated());
            Object principal = authentication.getPrincipal();

            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else {
                return principal.toString();
            }
        }
        return null;
}
    private static final Logger log = LoggerFactory.getLogger(RoomController.class);
    private String generateNewFileId() {
        return UUID.randomUUID().toString();
    }
    @PostMapping("/rooms/{roomId}/clone")
    public ResponseEntity<Object> cloneRoom(
            @PathVariable String roomId,
            @RequestParam String newRoomId,
            @RequestParam String newRoomName,
            @RequestParam String username) {

        // Check permissions
        if (!hasEditPermission(roomId, username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have permission to clone this room."));
        }

        // Fetch the original room
        Room originalRoom = roomRepository.findById(roomId).orElse(null);
        if (originalRoom == null) {
            log.error("Original room with ID {} not found", roomId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Room not found"));
        }

        log.info("Original room fetched: {}", originalRoom);

        // Create new room instance
        Room clonedRoom = new Room();
        clonedRoom.setId(newRoomId);
        clonedRoom.setName(newRoomName);
        clonedRoom.setUuid(originalRoom.getUuid());
        clonedRoom.setOwner(originalRoom.getOwner());
        clonedRoom.setUserRoles(originalRoom.getUserRoles() != null ?
                new HashMap<>(originalRoom.getUserRoles()) : new HashMap<>()); // Avoid null

        // Add the user who cloned the room as a participant
        clonedRoom.getUserRoles().put(username, "EDITOR"); // Set role for the user

        // Create new file instances for the cloned room
        List<File> originalFiles = fileRepository.findByRoomId(roomId);
        List<File> clonedFiles = new ArrayList<>();

        if (originalFiles == null || originalFiles.isEmpty()) {
            log.warn("No files found for the original room ID: {}", roomId);
        } else {
            for (File originalFile : originalFiles) {
                File clonedFile = new File();
                clonedFile.setId(UUID.randomUUID().toString());
                clonedFile.setName(originalFile.getName());
                clonedFile.setContent(originalFile.getContent());
                clonedFile.setRoomId(newRoomId);
                clonedFile.setLastModified(originalFile.getLastModified());
                clonedFile.setOwner(originalFile.getOwner());
                clonedFile.setLang(originalFile.getLang());
                clonedFile.setVersions(originalFile.getVersions() != null ?
                        new ArrayList<>(originalFile.getVersions()) : new ArrayList<>()); // Avoid null
                clonedFile.setComments(originalFile.getComments() != null ?
                        new ArrayList<>(originalFile.getComments()) : new ArrayList<>()); // Avoid null
                clonedFiles.add(clonedFile);
            }
        }

        log.info("Cloned files for room ID {}: {}", newRoomId, clonedFiles);

        fileRepository.saveAll(clonedFiles);

        List<String> clonedFileIds = clonedFiles.stream().map(File::getId).collect(Collectors.toList());
        clonedRoom.setFileIds(clonedFileIds);

        roomRepository.save(clonedRoom);
        log.info("Cloned room created: {} with files: {}", clonedRoom.getId(), clonedFileIds);

        return ResponseEntity.ok(clonedRoom);
    }

    @PostMapping("/rooms/{roomId}/fork")
    public ResponseEntity<Object> forkRoom(@PathVariable String roomId,
                                           @RequestParam String newRoomId,
                                           @RequestParam String newRoomName,
                                           @RequestParam String username) {
        // Check permissions
        if (!hasEditPermission(roomId, username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have permission to fork this room."));
        }

        // Fetch the original room
        Room originalRoom = roomRepository.findById(roomId).orElse(null);
        if (originalRoom == null) {
            log.error("Original room with ID {} not found", roomId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Room not found"));
        }

        log.info("Original room fetched for forking: {}", originalRoom);

        // Create new room instance
        Room forkedRoom = new Room();
        forkedRoom.setId(newRoomId);
        forkedRoom.setName(newRoomName); // Set new room name
        forkedRoom.setUuid(originalRoom.getUuid());
        forkedRoom.setOwner(originalRoom.getOwner());

        // Copy user roles from the original room
        forkedRoom.setUserRoles(new HashMap<>(originalRoom.getUserRoles()));

        // Add the user who forked the room as a participant
        forkedRoom.getUserRoles().put(username, "EDITOR"); // Set role for the user

        // Copy file details
        List<String> originalFileIds = originalRoom.getFileIds();
        List<File> clonedFiles = new ArrayList<>();

        for (String fileId : originalFileIds) {
            File originalFile = fileRepository.findById(fileId).orElse(null);
            if (originalFile != null) {
                File clonedFile = new File();
                clonedFile.setId(UUID.randomUUID().toString()); // Generate a new ID for the cloned file
                clonedFile.setName(originalFile.getName()); // Copy file name
                clonedFile.setContent(originalFile.getContent()); // Copy file content
                clonedFile.setLang(originalFile.getLang()); // Copy file language
                clonedFile.setLastModified(originalFile.getLastModified()); // Copy last modified date
                clonedFile.setOwner(originalFile.getOwner());
                clonedFile.setRoomId(newRoomId); // Set the new room ID
                clonedFile.setComments(originalFile.getComments() != null ?
                        new ArrayList<>(originalFile.getComments()) : new ArrayList<>()); // Avoid null
                clonedFile.setVersions(originalFile.getVersions() != null ?
                        new ArrayList<>(originalFile.getVersions()) : new ArrayList<>()); // Avoid null
                clonedFiles.add(clonedFile); // Add to list of cloned files
            }
        }

        log.info("Cloned files for room ID {}: {}", newRoomId, clonedFiles);

        // Save cloned files to the repository
        fileRepository.saveAll(clonedFiles);

        // Update forked room with the IDs of the cloned files
        List<String> clonedFileIds = clonedFiles.stream().map(File::getId).collect(Collectors.toList());
        forkedRoom.setFileIds(clonedFileIds);

        // Save the forked room
        roomRepository.save(forkedRoom);
        log.info("Forked room created: {} with files: {}", forkedRoom.getId(), clonedFileIds);

        return ResponseEntity.ok(forkedRoom);
    }

    @PostMapping("/rooms/merge")
    public ResponseEntity<String> mergeFiles(@RequestParam String sourceRoomName,
                                             @RequestParam String sourceFileName,
                                             @RequestParam String targetRoomName,
                                             @RequestParam String targetFileName,
                                             @RequestParam String username) {
        // Fetch the room IDs based on room names
        Room sourceRoom = roomRepository.findByName(sourceRoomName);
        Room targetRoom = roomRepository.findByName(targetRoomName);

        if (sourceRoom == null || targetRoom == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Source or target room not found.");
        }

        // Fetch the source and target files using the room IDs and file names
        File sourceFile = fileRepository.findByRoomIdAndName(sourceRoom.getId(), sourceFileName);
        File targetFile = fileRepository.findByRoomIdAndName(targetRoom.getId(), targetFileName);

        System.out.println("Looking for source file: " + sourceFileName + " in room: " + sourceRoomName);
        System.out.println("Looking for target file: " + targetFileName + " in room: " + targetRoomName);

        // Check if both files exist
        if (sourceFile != null && targetFile != null) {
            // Merge content from source into target
            String mergedContent = mergeFileContents(sourceFile.getContent(), targetFile.getContent());

            // Update the target file with merged content
            targetFile.setContent(mergedContent);

            // Also update the source file with the same merged content
            sourceFile.setContent(mergedContent);

            // Save both files to the repository
            fileRepository.save(targetFile);
            fileRepository.save(sourceFile);

            return ResponseEntity.ok("Files merged successfully");
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Files not found");
    }

    private String mergeFileContents(String sourceContent, String targetContent) {
        // Implement a merge strategy (e.g., last write wins, etc.)
        return sourceContent + "\n" + targetContent; // Simple example, can be improved
    }
    private boolean hasEditPermission(String roomId, String username) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return false;
        }
        String role = room.getUserRoles().get(username);
        return "ADMIN".equals(role);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll(); // Fetch all users from the database
        return ResponseEntity.ok(users);
    }

    // Method to assign role to a user in a specific room
    @PostMapping("/rooms/{roomId}/assignRole")
    public ResponseEntity<String> assignRoleToUser(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {

        String username = request.get("username");
        String role = request.get("role"); // Expected role: ADMIN, EDITOR, VIEWER

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found.");
        }
        // Ensure only the ADMIN can assign roles
        String currentAdminRole = room.getUserRoles().get(request.get("assignerUsername"));
        if (!"ADMIN".equals(currentAdminRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to assign roles.");
        }
        // Check if user exists
        Optional<User> optionalUser = userRepository.findByUsername(username); // Get Optional<User>
        if (optionalUser.isEmpty()) { // Check if the user is present
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User not found.");
        }

        User user = optionalUser.get(); // Get the User from the Optional
        System.out.println("Room before update: " + room);
        System.out.println("Assigning role: " + role + " to user: " + username);

        // Assign role and add user to the room
        room.getUserRoles().put(username, role);
        if (!room.getParticipants().contains(user.getUserId())) { // Use user.getUserId()
            room.getParticipants().add(user.getUserId());
            System.out.println("User added to participants: " + username);
        } else {
            System.out.println("User already a participant: " + username);
        }
        roomRepository.save(room);

        return ResponseEntity.ok("Role assigned successfully to user: " + username);
    }
    @GetMapping("/rooms/{roomId}/participants")
    public ResponseEntity<List<Map<String, String>>> getRoomParticipants(@PathVariable String roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        List<Map<String, String>> participantsWithRoles = new ArrayList<>();

        // Loop through participants and add their username and role to the response
        for (String userId : room.getParticipants()) {
            Optional<User> optionalUser = userRepository.findById(userId);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                String role = room.getUserRoles().get(user.getUsername());
                Map<String, String> participant = new HashMap<>();
                participant.put("username", user.getUsername());
                participant.put("role", role);
                participantsWithRoles.add(participant);
            }
        }
        return ResponseEntity.ok(participantsWithRoles);
    }  @GetMapping("/favicon.ico")
    public void favicon() {
    }
}
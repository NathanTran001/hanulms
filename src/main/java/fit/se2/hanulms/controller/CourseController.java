package fit.se2.hanulms.controller;

import fit.se2.hanulms.repository.*;
import fit.se2.hanulms.model.*;
import fit.se2.hanulms.model.DTO.CourseDTO;
import fit.se2.hanulms.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class CourseController {
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    FacultyRepository facultyRepository;
    @Autowired
    LecturerRepository lecturerRepository;
    @Autowired
    StudentRepository studentRepository;

    // ============== COURSE ENDPOINTS ==============

    @GetMapping("/courses")
    public ResponseEntity<Page<CourseDTO>> getCourses(
            HttpServletRequest request,
            Pageable pageable) {
        String username = jwtUtil.extractUsername(jwtUtil.extractTokenFromCookie(request));

        // Check if user is a student
        Optional<Student> studentOpt = studentRepository.findByUsername(username);
        if (studentOpt.isPresent()) {
            Page<Course> courses = courseRepository.findAllByStudentsContaining(studentOpt.get(), pageable);
            Page<CourseDTO> courseDTOs = courses.map(CourseDTO::new);
            return ResponseEntity.ok(courseDTOs);
        }

        // Check if user is a lecturer
        Optional<Lecturer> lecturerOpt = lecturerRepository.findByUsername(username);
        if (lecturerOpt.isPresent()) {
            Page<Course> courses = courseRepository.findAllByLecturersContaining(lecturerOpt.get(), pageable);
            Page<CourseDTO> courseDTOs = courses.map(CourseDTO::new);
            return ResponseEntity.ok(courseDTOs);
        }

        // User not found - return empty page with proper message
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build();
    }

    // Shared endpoint for both students and lecturers to get course details
    @GetMapping("/courses/{code}")
    public ResponseEntity<CourseDTO> getCourse(HttpServletRequest request, @PathVariable String code) {
        String username = jwtUtil.extractUsername(jwtUtil.extractTokenFromCookie(request));
        Optional<Course> courseOpt = courseRepository.findById(code);
        if (courseOpt.isPresent()) {
            // Check if user is a student
            Optional<Student> studentOpt = studentRepository.findByUsername(username);
            if (studentOpt.isPresent()) {
                return ResponseEntity.ok(new CourseDTO(courseOpt.get()));
            }
            // Check if user is a lecturer
            Optional<Lecturer> lecturerOpt = lecturerRepository.findByUsername(username);
            if (lecturerOpt.isPresent()) {
                // Check if lecturer is associated with the course
                if (courseOpt.get().getLecturers().contains(lecturerOpt.get())) {
                    return ResponseEntity.ok(new CourseDTO(courseOpt.get()));
                }
            }

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);

        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/courses")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<?> createCourse(@RequestBody CourseRequest request) {
        try {
            // Validate basic course data
            List<String> errorMessages = validateCourse(request.getCode(), request.getName(),
                    request.getDescription(), request.getEnrolmentKey(), "create");

            // Validate faculty exists
            if (request.getFacultyCode() == null || request.getFacultyCode().trim().isEmpty()) {
                errorMessages.add("Faculty code is required");
            } else {
                Optional<Faculty> faculty = facultyRepository.findById(request.getFacultyCode());
                if (faculty.isEmpty()) {
                    errorMessages.add("Faculty with code '" + request.getFacultyCode() + "' not found");
                }
            }

            // Validate lecturers exist
            if (request.getLecturerIds() == null || request.getLecturerIds().isEmpty()) {
                errorMessages.add("At least one lecturer must be selected");
            } else {
                List<Lecturer> foundLecturers = lecturerRepository.findAllById(request.getLecturerIds());
                if (foundLecturers.size() != request.getLecturerIds().size()) {
                    errorMessages.add("One or more selected lecturers not found");
                }
            }

            if (!errorMessages.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("errors", errorMessages));
            }

            // Create course
            Course course = new Course();
            course.setCode(request.getCode());
            course.setName(request.getName());
            course.setEnrolmentKey(request.getEnrolmentKey());
            course.setDescription(request.getDescription());

            // Set faculty
            Optional<Faculty> faculty = facultyRepository.findById(request.getFacultyCode());
            if (faculty.isPresent()) {
                course.setFaculty(faculty.get());
            }

            // Set lecturers
            if (request.getLecturerIds() != null && !request.getLecturerIds().isEmpty()) {
                List<Lecturer> lecturers = lecturerRepository.findAllById(request.getLecturerIds());
                course.setLecturers(lecturers);

                // Update lecturer's course list (if bidirectional relationship)
                for (Lecturer lecturer : lecturers) {
                    if (lecturer.getCourses() == null) {
                        lecturer.setCourses(new ArrayList<>());
                    }
                    lecturer.getCourses().add(course);
                }
            }

            Course savedCourse = courseRepository.save(course);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedCourse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create course: " + e.getMessage()));
        }
    }

    @PutMapping("/courses/{code}")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<?> updateCourse(@PathVariable String code,
                                          @RequestBody CourseRequest request) {
        try {
            Optional<Course> optionalCourse = courseRepository.findById(code);
            if (!optionalCourse.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            // Ensure course code cannot be updated (security check)
            if (request.getCode() != null && !request.getCode().equals(code)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Course code cannot be modified"));
            }

            // Validate basic course data (using the same validation as create)
            List<String> errorMessages = validateCourse(code, request.getName(),
                    request.getDescription(), request.getEnrolmentKey(), "edit");

            // Validate faculty exists
            if (request.getFacultyCode() == null || request.getFacultyCode().trim().isEmpty()) {
                errorMessages.add("Faculty code is required");
            } else {
                Optional<Faculty> faculty = facultyRepository.findById(request.getFacultyCode());
                if (faculty.isEmpty()) {
                    errorMessages.add("Faculty with code '" + request.getFacultyCode() + "' not found");
                }
            }

            // Validate lecturers exist
            if (request.getLecturerIds() == null || request.getLecturerIds().isEmpty()) {
                errorMessages.add("At least one lecturer must be selected");
            } else {
                List<Lecturer> foundLecturers = lecturerRepository.findAllById(request.getLecturerIds());
                if (foundLecturers.size() != request.getLecturerIds().size()) {
                    errorMessages.add("One or more selected lecturers not found");
                }
            }

            if (!errorMessages.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("errors", errorMessages));
            }

            Course course = optionalCourse.get();

            // Clear existing lecturer associations (bidirectional cleanup)
            if (course.getLecturers() != null) {
                for (Lecturer lecturer : course.getLecturers()) {
                    if (lecturer.getCourses() != null) {
                        lecturer.getCourses().remove(course);
                    }
                }
            }
            course.getLecturers().clear();

            // Update course fields
            course.setName(request.getName());
            course.setEnrolmentKey(request.getEnrolmentKey());
            course.setDescription(request.getDescription());

            // Set faculty
            Optional<Faculty> faculty = facultyRepository.findById(request.getFacultyCode());
            if (faculty.isPresent()) {
                course.setFaculty(faculty.get());
            }

            // Set lecturers (same logic as create)
            if (request.getLecturerIds() != null && !request.getLecturerIds().isEmpty()) {
                List<Lecturer> lecturers = lecturerRepository.findAllById(request.getLecturerIds());
                course.setLecturers(lecturers);

                // Update lecturer's course list (bidirectional relationship)
                for (Lecturer lecturer : lecturers) {
                    if (lecturer.getCourses() == null) {
                        lecturer.setCourses(new ArrayList<>());
                    }
                    lecturer.getCourses().add(course);
                }
            }

            Course updatedCourse = courseRepository.save(course);
            return ResponseEntity.ok(updatedCourse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update course: " + e.getMessage()));
        }
    }

    @DeleteMapping("/courses/{code}")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<?> deleteCourse(@PathVariable String code) {
        try {
            Optional<Course> optionalCourse = courseRepository.findById(code);
            if (optionalCourse.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Course not found"));
            }

            Course course = optionalCourse.get();

            // Manually remove relationships before deleting
            // Remove course from all lecturers
            for (Lecturer lecturer : course.getLecturers()) {
                lecturer.getCourses().remove(course);
            }

            // Remove course from all students
            for (Student student : course.getStudents()) {
                student.getCourses().remove(course);
            }

            // Clear the relationships from course side
            course.getLecturers().clear();
            course.getStudents().clear();

            courseRepository.delete(course);

            return ResponseEntity.ok(Map.of("message", "Course deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete course"));
        }
    }

    @PostMapping("/courses/{code}/enrolment")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public ResponseEntity<?> enroll(@PathVariable String code,
                                    @RequestParam String enrolmentKey,
                                    HttpServletRequest request) {
        String username = jwtUtil.extractUsername(jwtUtil.extractTokenFromCookie(request));
        if (username == null) {
            return ResponseEntity.badRequest()
                    .body(List.of("Unable to extract username from request"));
        }
        Optional<Student> stuOpt = studentRepository.findByUsername(username);
        if (stuOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(List.of("User not found with username: " + username));
        }

        Optional<Course> courseOpt = courseRepository.findById(code);
        if (courseOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(List.of("Course not found with course code: " + code));
        }

        Course course = courseOpt.get();
        Student student = stuOpt.get();

        if (student.getCourses().contains(course)) {
            return ResponseEntity.badRequest()
                    .body(List.of("Student is already enrolled in this course"));
        }

        if (!course.getEnrolmentKey().equals(enrolmentKey)) {
            return ResponseEntity.badRequest()
                    .body(List.of("Invalid enrolment key!"));
        }

        course.getStudents().add(student);
        student.getCourses().add(course);

        studentRepository.save(student);
        courseRepository.save(course);
        return ResponseEntity.ok(new CourseDTO(course));
    }

    // Search coruse
    @GetMapping("/courses/search")
    public ResponseEntity<?> searchCourses(@RequestParam String searchPhrase,
                                           Pageable pageable,
                                           HttpServletRequest request) {
        String username = jwtUtil.extractUsername(jwtUtil.extractTokenFromCookie(request));
        if (username == null) {
            return ResponseEntity.badRequest()
                    .body(List.of("Unable to extract username from request"));
        }

        Optional<Student> stuOpt = studentRepository.findByUsername(username);
        Optional<Lecturer> lectOpt = lecturerRepository.findByUsername(username);

        // Check if user exists as either student or lecturer
        if (stuOpt.isEmpty() && lectOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(List.of("User not found with username: " + username));
        }

        // Get the faculty ID from either student or lecturer
        String facultyCode;
        if (stuOpt.isPresent()) {
            facultyCode = stuOpt.get().getFaculty().getCode();
        } else {
            facultyCode = lectOpt.get().getFaculty().getCode();
        }

        // Search courses by name/code AND filter by faculty
        Page<Course> courses = courseRepository.searchCoursesByFaculty(
                searchPhrase, facultyCode, pageable);

        return ResponseEntity.ok(courses.map(CourseDTO::new));
    }
    // ============== HELPER METHODS ==============

    private List<String> validateCourse(String courseCode, String courseName,
                                        String description, String enrolmentKey, String mode) {
        List<String> errorMessages = new ArrayList<>();

        if (mode.equals("create")) {
            if (courseRepository.existsById(courseCode)) {
                errorMessages.add("The entered course code already exists!");
            }
        }

        if (courseCode != null && courseCode.length() > 10) {
            errorMessages.add("The maximum length for course code is 10 characters!");
        }
        if (courseName != null && courseName.length() > 40) {
            errorMessages.add("The maximum length for course name is 40 characters!");
        }
        if (description != null && description.length() > 254) {
            errorMessages.add("The maximum length for course description is 254 characters!");
        }
        if (enrolmentKey != null && enrolmentKey.length() > 10) {
            errorMessages.add("The maximum length for enrolment key is 10 characters!");
        }

        return errorMessages;
    }

}

package fi.helsinki.cs.tmc.cli.command;

import fi.helsinki.cs.tmc.cli.command.core.AbstractCommand;
import fi.helsinki.cs.tmc.cli.command.core.Command;
import fi.helsinki.cs.tmc.cli.io.Color;
import fi.helsinki.cs.tmc.cli.io.ExternalsUtil;
import fi.helsinki.cs.tmc.cli.io.Io;
import fi.helsinki.cs.tmc.cli.tmcstuff.CourseInfo;
import fi.helsinki.cs.tmc.cli.tmcstuff.CourseInfoIo;
import fi.helsinki.cs.tmc.cli.tmcstuff.TmcUtil;
import fi.helsinki.cs.tmc.cli.tmcstuff.WorkDir;

import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.List;

@Command(name = "exercises", desc = "List the exercises for a specific course")
public class ListExercisesCommand extends AbstractCommand {

    private Io io;

    @Override
    public void getOptions(Options options) {
        options.addOption("n", "no-pager", false, "Don't use a pager to list the exercises");
        options.addOption("i", false, "Get the list of exercises from the server");
    }

    @Override
    public void run(CommandLine args, Io io) {
        this.io = io;
        String courseName;

        // Get course name
        String[] stringArgs = args.getArgs();
        if (stringArgs.length == 0) {
            courseName = getCourseNameFromCurrentDirectory();
            if (courseName == null) {
                return;
            }
        } else {
            courseName = stringArgs[0];
        }

        // Get course exercises
        List<Exercise> exercises;
        if (args.hasOption("i")) {
            exercises = getExercisesFromServer(courseName);
        } else {
            exercises = getLocalExercises(courseName);
        }
        if (exercises == null) {
            return;
        }

        printExercises(courseName, exercises, !args.hasOption("n"));
    }

    private String getCourseNameFromCurrentDirectory() {
        CourseInfo info = getCourseInfoFromCurrentDirectory();
        if (info == null) {
            this.io.println("No course specified. Either run the command in a course"
                    + " directory or enter the course as a parameter.");
            return null;
        }
        return info.getCourseName();
    }

    private CourseInfo getCourseInfoFromCurrentDirectory() {
        WorkDir workDir = getApp().getWorkDir();
        workDir.addPath();
        if (workDir.getConfigFile() != null) {
            return CourseInfoIo.load(workDir.getConfigFile());
        }
        return null;
    }

    private List<Exercise> getExercisesFromServer(String courseName) {
        TmcCore core = getApp().getTmcCore();
        if (core == null) {
            return null;
        }

        Course course = TmcUtil.findCourse(core, courseName);
        if (course == null) {
            this.io.println("Course '" + courseName + "' doesn't exist on the server.");
            return null;
        }

        List<Exercise> exercises = course.getExercises();
        if (exercises.isEmpty()) {
            this.io.println("Course '" + courseName + "' doesn't have any exercises.");
            return null;
        }
        return exercises;
    }

    private List<Exercise> getLocalExercises(String courseName) {
        CourseInfo info = getCourseInfoFromCurrentDirectory();
        if (info == null || !info.getCourseName().equals(courseName)) {
            this.io.println("You have to be in a course directory or use the -i option "
                    + "to get the exercises from the server.");
            return null;
        }

        List<Exercise> exercises = info.getExercises();
        if (exercises == null || exercises.isEmpty()) {
            this.io.println("Course '" + courseName + "' doesn't have any exercises.");
            return null;
        }
        return exercises;
    }

    private void printExercises(String courseName, List<Exercise> exercises, Boolean pager) {
        StringBuilder sb = new StringBuilder("Course name: " + courseName);
        String prevDeadline = "";

        for (Exercise exercise : exercises) {
            String deadline = getDeadline(exercise);
            if (!deadline.equals(prevDeadline)) {
                sb.append("\nDeadline: " + deadline + "\n");
                prevDeadline = deadline;
            }
            sb.append(getExerciseStatus(exercise));
        }

        if (pager) {
            ExternalsUtil.showStringInPager(sb.toString(), "exercise-list");
        } else {
            io.print(sb.toString());
        }
    }

    private String getDeadline(Exercise exercise) {
        String deadline = exercise.getDeadline();
        if (deadline == null) {
            return "not available";
        }
        deadline = deadline.substring(0, 19);
        return deadline.replace("T", " at ");
    }

    private String getExerciseStatus(Exercise exercise) {
        // Check the exercise status in order of flag importance, for example there's
        // no need to check if deadline has passed if the exercise has been submitted
        String status;
        if (exercise.isCompleted()) {
            if (exercise.requiresReview() && !exercise.isReviewed()) {
                status = Color.colorString("  Requires review: ", Color.AnsiColor.ANSI_YELLOW);
            } else {
                status = Color.colorString("  Completed: ", Color.AnsiColor.ANSI_GREEN);
            }
        } else if (exercise.hasDeadlinePassed()) {
            status = Color.colorString("  Deadline passed: ", Color.AnsiColor.ANSI_PURPLE);
        } else if (exercise.isAttempted()) {
            status = Color.colorString("  Attempted: ", Color.AnsiColor.ANSI_BLUE);
        } else {
            status = Color.colorString("  Not completed: ", Color.AnsiColor.ANSI_RED);
        }

        status += exercise.getName() + "\n";
        return status;
    }
}

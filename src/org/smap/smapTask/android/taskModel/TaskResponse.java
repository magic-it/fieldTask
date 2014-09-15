package org.smap.smapTask.android.taskModel;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.odk.collect.android.database.TaskAssignment;

public class TaskResponse {

	public String message;
	public String status;
	public String deviceId;
	@SerializedName("data")
	public List<TaskAssignment> taskAssignments;
	public List<FormLocator> forms;
}

package org.smap.smapTask.android.taskModel;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class UserDetailResponse {

	public String message;
	public String status;
	@SerializedName("data")
	public List<UserDetail> userList;
}

package com.runepixels.clan;

import java.util.List;
import lombok.Value;

@Value
class ClanPayload
{
	long accountHash;
	String name;
	List<Member> members;

	@Value
	static class Member
	{
		String name;
		int rank;
	}
}

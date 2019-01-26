package com.jetoff.learning;

import java.util.TreeSet;

/**
 * Created by Alain on 02/09/2018.
 */
public class Tree extends TreeSet
{
	private Object root;
	private Object leaf;
	public int score;
	public Tree() {
		//regions = new ArrayList<HashSet<Integer>>[5];
	}

	public Object searchTree()
	{
		return root;
	}
}

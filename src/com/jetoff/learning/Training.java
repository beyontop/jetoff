package com.jetoff.learning;

import com.jetoff.gui.Sudoku;
import com.jetoff.gui.SudokuGUI;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by Alain on 27/01/2019.
 */
public class Training extends Thread
{
	private Cell[][] cells;
	private short clueCount;
	private Integer [][] grid = new Integer[9][9];
	private static int MAX_POSSIBLE_SCORE = 0;
	private static int TARGET;
	private static int SCORE = 0;
	private boolean SOLUTION_REACHED = false;
	/**
	 *  The constraint under which the training are the three below,
	 *  according to
	 */
	private ArrayList<HashSet<Integer>> columns = new ArrayList<>( 9 );
	private ArrayList<HashSet<Integer>> rows    = new ArrayList<>( 9 );
	private ArrayList<HashSet<Integer>> regions = new ArrayList<>( 9 );
	protected HashSet<Cell> InitialSet = new HashSet<>();
	private static HashSet<Cell> pool = new HashSet<>();
	Tree tree;
	private _DecisionSpace dSpace;
	private _SolutionSpace sSpace;

	zzTop d = new zzTop();
	int ITERATION_COUNT = 0;


	public Training( short clueCount )
	{
		this.clueCount = clueCount;
		MAX_POSSIBLE_SCORE = 81 - clueCount;
		cells = new Cell[9][9];
		dSpace = new _DecisionSpace();
		sSpace = new _SolutionSpace();
//		dSpace.leastVisited.visitCount = 0;
	}

	public void run()
	{
		String title = "Maximum score: ";
		d.addText( title + MAX_POSSIBLE_SCORE +"\n" );
		prepareData();
		TARGET = MAX_POSSIBLE_SCORE;
		try {
			while( SCORE != TARGET )
			{
				buildTree();
				mergeSolutions();
			}
		}
		catch ( Exception e ) {
			e.printStackTrace();
		}
		finally {
			System.out.println( "Nombre d'itérations: "+ ITERATION_COUNT );
		}
	}

	public void setRows( ArrayList<HashSet<Integer>> rows )
	{
		this.rows = rows;
	}
	public void setColumns( ArrayList<HashSet<Integer>> columns )
	{
		this.columns = columns;
	}
	public void setRegions( ArrayList<HashSet<Integer>> regions )
	{
		this.regions = regions;
	}

	private void prepareData()
	{
		int n = 0;
		for( int i = 0; i < 9; i++ )
		{
			for( int j = 0; j < 9; j++ )
			{
				if( Sudoku.isNotFilledCellOnPuzzle[i][j] )
				{
					n += 1;
					Cell cell = new Cell();
					cell.setRow(i);
					cell.setColumn(j);
					int k = getRegionIndex(i,j);
					cell.setRegion(k);
					cell.domain = getDomain( rows.get(i), columns.get(j), regions.get(k) );
					InitialSet.add( cell );
					pool.add( cell );
					cells[i][j] = cell;
					d.addText( "At: "+n+"- ["+cell.row+","+cell.column+"] Domain: "+cell.domain );
				}
				else
					cells[i][j] = null;
			}
		}
		d.addText( "Taille du pool: "+pool.size() );
	}

	private static HashSet<Integer> getDomain( HashSet<Integer> row,
											   HashSet<Integer> column,
											   HashSet<Integer> region )
	{
		HashSet<Integer> set = new HashSet<>(9 );
		for ( Integer n : row ) set.add(n);
		for ( Integer n : column ) set.add(n);
		for ( Integer n : region ) set.add(n);
		return getComplement( set );
	}

	private static HashSet<Integer> getComplement( HashSet<Integer> set )
	{
		HashSet<Integer> s = new HashSet<>(9 );
		for( int i = 1; i <= 9; i++ )
		{
			if( !set.contains( i ))
				s.add( i );
		}
		return s;
	}

	private void buildTree()
	{
		Node leaf, root = new Node();
		root.parent = null;
		Cell cell = dSpace.bestCell( dSpace::firstRound );
		cell.visitCount = 1;
		dSpace.init( cell );
		Node top = new Node( cell, root );
		tree = new Tree( root );
		tree.addToTree( root, top );
		if( hitCells( cell.row, cell.column, cell.region, cell ))
		{
			top.score = 1;
			d.addText( "Score = "+top.score+", [" + top.cell.row +"," + top.cell.column +"] = " + top.cell.value );
		}
		System.out.println( "Entré boucle principale..." );
		leaf = top;
		while ( !SOLUTION_REACHED && ITERATION_COUNT < 20 )
		{
			Node lastNode = _rollOut( leaf );				// Sampling/Expansion.
			if( lastNode.score == MAX_POSSIBLE_SCORE )
				SOLUTION_REACHED = true;
			backPropagate( lastNode );						// Back-propagation.
			leaf = applyPolicy( lastNode );					// Selection.
			ITERATION_COUNT++;
		}
		d.addText( "Nombre d'itérations: "+ ITERATION_COUNT );
	}

	private Node _rollOut( Node node )
	{
		int score = node.score;
		boolean TERMINAL_STATE = false;
		Node currentNode, parentNode = node;
		do
		{
			currentNode = new Node( dSpace.bestCell( dSpace::secondRound ), parentNode );
			currentNode.cell.visitCount += 1;
			/**
			 * Cells Essay
			 */
			if( hitCells( currentNode.cell.row, currentNode.cell.column, currentNode.cell.region, currentNode.cell ))
			{
				currentNode.score = parentNode.score + 1;
				update( currentNode.cell );
				if( currentNode.score == MAX_POSSIBLE_SCORE )
					return currentNode;
				parentNode = currentNode;
				d.addText( "Score = "+currentNode.score+", [" + currentNode.cell.row +"," + currentNode.cell.column +"] = "
						+ currentNode.cell.value +" Visits: "+currentNode.cell.visitCount);
			}
			else
			{
				TERMINAL_STATE = true;
				d.addText(" Terminal state, iteration = "+ITERATION_COUNT );
			}
			if( pool.size() == 0 ) {
				//mirrorSpaces();
				pool.addAll( InitialSet );
				d.addText(" After re-filling, size = "+pool.size() );
				return currentNode;
			}
		}
		while(/*( pool.size() != 0 ) && */ !TERMINAL_STATE );
		return currentNode;
	}

	private boolean hitCells( int i, int j, int k, Cell cell )
	{
		/**
		 *  Hits the given "cell", according to this one's domain.
		 */
		for( Iterator<Integer> CellDomain = cell.getDomainItems(); CellDomain.hasNext(); )
		{
			Integer value = CellDomain.next();
			cell.value = value;
			if( rows.get(i).add( value ))
			{
				if( columns.get(j).add( value ))
				{
					if( regions.get(k).add( value ))
					{
						cell.domain.remove( value );
						sSpace.valueHit( cell );
						return true;
					}
					else
					{
						columns.get(j).remove( value );
						rows.get(i).remove( value );
					}
				}
				else
				{
					rows.get(i).remove( value );
				}
			}
		}
		dSpace.lastTrial = cell;
		d.addText( "Last Trial: [" + dSpace.lastTrial.row +"," + dSpace.lastTrial.column +"] = "
				+ dSpace.lastTrial.value );
		return false;
	}

	private Node applyPolicy( Node node )
	{
		// tester "is top of the tree in he node itself
		if( node.parent == null ) return node;
		return node.parent;
	}

	private void backPropagate( Node node )
	{
		if( node.parent == null ) return;
		node.visitCount += 1;
		node.reward += node.score;
		backPropagate( node.parent );
	}

	void update( Cell c )
	{
		dSpace.update( c );
		sSpace.update( c );
	}

	void mergeSolutions()
	{
		;
	}

	void mirrorSpaces()
	{
		dSpace.accept( sSpace.mirror() );
	}

	private static int getRegionIndex( int i, int j )
	{
		int k = 0;
		if( i < 3 && j < 3 ) ;
		if( i < 3 && j >= 3 && j < 6 ) k = 1;
		if( i < 3 && j >= 6 ) k = 2;
		if( i >= 3 && i < 6 && j < 3 ) k = 3;
		if( i >= 3 && i < 6 && j >= 3 && j < 6 ) k = 4;
		if( i >= 3 && i < 6 && j >= 6 ) k = 5;
		if( i >= 6 && j < 3 ) k = 6;
		if( i >= 6 && j >= 3 && j < 6 ) k = 7;
		if( i >= 6 && j >= 6 ) k = 8;
		return k;
	}

	class Cell implements Comparable<Cell>
	{
		private Integer value;
		private int row;
		private int column;
		private int region;
		protected int visitCount;
		HashSet<Integer> domain;
		Cell(){
			domain = new HashSet<>(9 );
		}
		void setRow( int i ) {
			row = i;
		}
		void setColumn( int j ) {
			column = j;
		}
		void setRegion( int k ) {
			region = k;
		}
		int getDomainSize() { return domain.size(); }

		Iterator<Integer> getDomainItems()
		{
			return domain.iterator();
		}

		@Override
		public int compareTo( Cell o )
		{
			int v = 0;
			if( this.getDomainSize() < o.domain.size() ) v = -1;
			if( this.getDomainSize() > o.domain.size() ) v = +1;
			if( this.getDomainSize() == o.domain.size() ) v = 0;
			return v;
		}
	}

	class Node implements Comparable<Node>
	{
		private Cell cell;
		private Node parent;
		private int score;
		private int reward;
		private int visitCount;
		private HashSet<Node> children = new HashSet<>();

		Node()
		{
			this.cell = null;
			this.parent = null;
			score = 0;
			reward = 0;
			visitCount = 0;
		}

		Node( Cell cell, Node parent )
		{
			this.cell = cell;
			this.parent = parent;
			score = 0;
			reward = 0;
			visitCount = 0;
		}

		void addChild( Node child )
		{
			children.add( child );
		}

		boolean isLeaf()
		{
			return this.children.isEmpty();
		}

		@Override
		public int compareTo( Node o )
		{
			int k = 0;
			if( this.score == o.score )
			{
				if( this.reward < o.reward ) k = -1;
				if( this.reward == o.reward ) k = 0;
				if( this.reward > o.reward ) k = 1;
			}
			if( this.score < o.score ) k = -1;
			if( this.score > o.score ) k = 1;
			return k;
		}
	}

	interface _Space<T> {
		void cloneData();
		boolean insert(T t);
		void classify();
		void merge();
		void split();
	}

	interface _Functor {
		Function<Cell, Node> map();
	}

	class _DecisionSpace implements _Space, _Functor
	{
		Node n;
		private Cell leastVisited, lastTrial;
		private boolean miRRor[][] = new boolean[9][9];

		_DecisionSpace()
		{
			n = new Node();
			cells = new Cell[9][9];
			for( int i = 0; i < 9; i++ )
				for( int j = 0; j < 9; j++ ) {
					miRRor[i][j] = false;
				}
		}

		void update( Cell c )
		{
			if( c.visitCount < leastVisited.visitCount )
				leastVisited = c;
			miRRor[c.row][c.column] = ( c.value != 0 );
		}

		Cell bestCell( Supplier<Cell> b )
		{
			return b.get();
		}

		Cell firstRound() {

			Cell c = Collections.min( pool );
			pool.remove(c);
			return c;
		}

		Cell secondRound() {

			Cell c = Collections.min( pool );
			if( c.visitCount < leastVisited.visitCount )
			{
				pool.remove(c);
				return c;
			}
			else
				return leastVisited;
		}

		void init( Cell c )
		{
			leastVisited = c;
			leastVisited.visitCount = c.visitCount;
		}

		void accept( boolean[][] b )
		{
			Iterator<Cell> I = InitialSet.iterator();
			while( I.hasNext() )
			{
				Cell c = I.next();
				int i = c.row, j = c.column;
				if( b[i][j] )
					pool.add(c);
			}
		}

		@Override
		public void cloneData() {
			/*pool = new HashSet<>( InitialSet )*/;
		}
		@Override
		public boolean insert( Object o ) {
			return false;
		}
		@Override
		public void classify() {

		}
		@Override
		public void merge() {

		}
		@Override
		public void split() {

		}
		@Override
		public Function<Cell, Node> map() {
			return null;
		}
	}

	private class _SolutionSpace implements _Space, _Functor
	{
		Cell[][] solution;
		_SolutionSpace() {
			solution = new Cell[9][9];
		}
		void update( Cell c )
		{
			int i = c.row, j = c.column;
			solution[i][j] = c;

		}
		void valueHit( Cell c )
		{
			solution[c.row][c.column] = c;
		}

		boolean [][] mirror()
		{
			boolean [][] b = new boolean[9][9];
			for( int i = 0; i < 9; i++ )
				for( int j = 0; j < 9; j++ ) {
					if( solution[i][j] != null )
						b[i][j] = false;
					else
						b[i][j] = true;
				}
			return b;
		}
		@Override
		public void cloneData() {

		}
		@Override
		public boolean insert(Object o) {
			return false;
		}
		@Override
		public void classify() {

		}
		@Override
		public void merge() {

		}
		@Override
		public void split() {

		}
		@Override
		public Function<Cell, Node> map() {
			return null;
		}
	}
	class Tree
	{
		private Node root;
		private Cell leastVisitedCell;
		private Set<Cell> pool;
		Tree( Node root )
		{
			this.root = root;
		}
		void addToTree( Node parentNode, Node childNode )
		{
			parentNode.addChild( childNode );
		}
		void insertCellInTree( Cell c )
		{

		}
		Cell getLeastVisitedCell()
		{
			return leastVisitedCell;
		}
	}

	class zzTop
	{
		JFrame w;
		JTextArea textToDisplay;
		JScrollPane resultsPane;
		zzTop() {
			w = SudokuGUI.getWindow( 8888, "Résultats" );
			w.setLocation( 700, 300 );
			textToDisplay = new JTextArea( "Comments:\n" );
			//textToDisplay.setFont( Font.ROMAN_BASELINE );
			resultsPane = new JScrollPane( textToDisplay );
			w.add( resultsPane );
			w.setVisible( true );
		}
		void addText( String text )
		{
			textToDisplay.append( text + "\n" );
		}
	}
}

package com.jetoff.learning;

import com.jetoff.gui.Sudoku;
import com.jetoff.gui.SudokuGUI;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;

/**
 * Created by Alain on 03/09/2018.
 */

public class _SearchTree extends Thread
{
	Cell[][] cells;
	Node leaf;
	Cell leastVisitedCell;
	private Tree tree;
	private ArrayList<HashSet<Integer>> columns = new ArrayList<>( 9 );
	private ArrayList<HashSet<Integer>> rows    = new ArrayList<>( 9 );
	private ArrayList<HashSet<Integer>> regions = new ArrayList<>( 9 );

	private HashSet<Cell> trialCells = new HashSet<>();

	private Integer [][] grid = new Integer[9][9];
	private _DecisionSpace decision;
	private _SolutionSpace solution;
	private short clueCount;
	private boolean SOLUTION_REACHED = false;
	private int      ITERATION_COUNT = 0;
	private int   MAX_POSSIBLE_SCORE = 0;
	zzTop d = new zzTop();

	public _SearchTree( short clueCount )
	{
		this.clueCount = clueCount;
		MAX_POSSIBLE_SCORE = 81 - clueCount;
		cells = new Cell[9][9];
		decision = new _DecisionSpace();
		solution = new _SolutionSpace();
	}

	public void run()
	{
		String title = "Maximum score: ";
		d.addText( title + MAX_POSSIBLE_SCORE +"\n" );
		prepareData();
		_buildTree();
		d.addText("Essais infructueux: "+decision.unsuccessfulTrials.size());
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
					trialCells.add( cell );
					//decision.addCell( cell );
					cells[i][j] = cell;
					d.addText( "At: "+n+"- ["+cell.row+","+cell.column+"] Domain: "+cell.domain );
				}
				else
					cells[i][j] = null;
			}
		}
		decision.duplicateGrid( cells );
		decision.cloneData();
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

	/**
	 * Gives the cell (i.e. variable) with the smallest domain size,
	 * according to the overriding method "compareTo".
	 */
	private Cell bestCell()
	{
		Cell cell = Collections.min( trialCells );
		trialCells.remove(cell);
		return cell;
	}

	private void _buildTree()
	{
		Node leaf, root = new Node();
		root.parent = null;
		Cell cell = decision.bestCell();
		cell.visitCount += 1;
		leastVisitedCell = cell;
		Node top = new Node( cell, root );
		tree = new Tree( root );
		tree.addToTree( root, top );
		if( trialSuccessFul( cell.row, cell.column, cell.region, cell ))
		{
			// gotta check that.
			top.score = 1;
			d.addText( "Score = "+top.score+", [" + top.cell.row +"," + top.cell.column +"] = " + top.cell.value );
		}
		while ( !SOLUTION_REACHED )
		{
			leaf = applyPolicy( top ); 						// Selection.
			Node lastNode = _rollOut( leaf );				// Expansion/Sampling.
			if( lastNode.score == MAX_POSSIBLE_SCORE )
				SOLUTION_REACHED = true;
			else
				top = lastNode;
			backPropagate( lastNode );						// Back-propagation.
			ITERATION_COUNT++;
		}
		d.addText( "Nombre d'itérations: "+ ITERATION_COUNT );
	}

	private Node applyPolicy( Node node )
	{
		if( node.parent == tree.root ) return node;
		return node.parent;
	}

	private Node _rollOut( Node node )
	{
		int score = node.score;
		boolean TERMINAL_STATE = false;
		Node currentNode, parentNode = node;
		do
		{
			currentNode = new Node( decision.bestCell(), parentNode );
			currentNode.cell.visitCount += 1;
			update( currentNode.cell );
			tree.addToTree( parentNode, currentNode );
			if( trialSuccessFul( currentNode.cell.row, currentNode.cell.column, currentNode.cell.region, currentNode.cell ))
			{
				currentNode.score = ++score;
				if( currentNode.score == MAX_POSSIBLE_SCORE )
					return currentNode;
				parentNode = currentNode;
				d.addText( "Score = "+currentNode.score+", [" + currentNode.cell.row +"," + currentNode.cell.column +"] = " + currentNode.cell.value );
			}
			else
			{
				TERMINAL_STATE = true;
			}
		}
		while( !TERMINAL_STATE );
		return currentNode;
	}
	/**
	 * We try all the elements of the cell's domain, until one that fits in,
	 * so then return true, -- false otherwise, nothing being done. Filling
	 * a cell that fits means store the play out "value" (from [1-9]),
	 * and add this value to row "i", column "j" and region "k", as a new
	 * constraint. The same "value" is removed from the cell's domain.
	 */
	private boolean trialSuccessFul( int i, int j, int k, Cell cell )
	{
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
						grid[i][j] = value;
						return true;
					}
					else
					{
						columns.get(j).remove( value );
						rows.get(i).remove( value );
						decision.unsuccessfulTrials.add( cell );
						decision.setLastUnsuccesfulTrial( cell );
					}
				}
				else
				{
					rows.get(i).remove( value );
					decision.unsuccessfulTrials.add( cell );
					decision.setLastUnsuccesfulTrial( cell );
				}
			}
		}
		return false;
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
		if( leastVisitedCell.visitCount > c.visitCount )
			leastVisitedCell = c;
	}

	public Integer[][] getGrid()
	{
		return grid;
	}

	private void prepareExport()
	{
		HashSet<String> v = new HashSet<>( MAX_POSSIBLE_SCORE );
		Node parentNode, currentNode = leaf;
		for( ;; )
		{
			parentNode = currentNode.parent;
			v.add( currentNode.cell.value.toString() );
			currentNode = parentNode;
			if( currentNode.parent == null ) break;
		}
		d.addText( "Taille vecteur des entiers: "+ v.size() );
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	class Cell implements Comparable<Cell>
	{
		private Integer value;
		private int row;
		private int column;
		private int region;
		private int visitCount;
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

	/**
	 * depth of the sample as its score (i. e. the number of variables
	 * that have been assigned before finding one with an empty domain)
	 */
	class Node implements Comparable<Node>
	{
		private Cell cell;
		private Node parent;
		private int score;
		private int reward;
		private int visitCount;
		private LinkedList<Node> children = new LinkedList<>();

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

		void addChildNode( Node child )
		{
			children.add( child );
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
			parentNode.addChildNode( childNode );
		}
		void insertCellInTree( Cell c )
		{

		}
		Cell getLeastVisitedCell()
		{
			return leastVisitedCell;
		}
	}

	/**
	 *  "_DecisionSpace" must uphold the domain (constraint variable)
	 * allowed to each cell, according to the subset of:
	 * 		d = { 0, 1, 3, 4, 5, 6 ,7, 8, 9 },
	 * made of elements not already disposed on row (i), column (j), region (k).
	 *  So we have:
	 * 		_D = d_i*d_j*d_k, i,j,k = [1-9],
	 * with _D denoting de "decision space". Hence, the map:
	 * 		f(x,y,z) -> C, f(x,y,z) -> (i,j),
	 * C being one of the 9x9 cells of the puzzle.
	 */
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
	/**
	 *  To each cell (i,j) is mapped a domain, that is, the free numbers
	 * allowed with respect to rows, columns, blocks. Therefore the decision
	 * space is:
	 * 		_D = U.d(i,j), i*j = [1-9]x[1-9],
	 */
	class _DecisionSpace implements _Space, _Functor
	{
		Node n; //minRewardAndScoreNode;
		private Cell lastTrial;
		private HashSet<Cell> pool = new HashSet<>();
		private Cell[][] cells;
		Iterator<Cell> i;
		HashSet<Cell> unsuccessfulTrials = new HashSet<>();
		_DecisionSpace() {
			n = new Node();
			cells = new Cell[9][9];
			//cloneData();
			//i = pool.iterator();
		}
		void update( Node currentNode )
		{
			if( this.getNode().compareTo( currentNode ) > 0 )
				this.n = currentNode;
		}
		Node getNode() {
			return n;
		}
		void duplicateGrid( Cell[][] grid )
		{
			for( int i = 0; i < 9; i++ )
				for( int j = 0; j < 9; j++ )
					this.cells[i][j] = grid[i][j];
		}
		void addCell( Cell c ) {
			pool.add( c );
		}

		Cell bestCell()
		{
			if( pool.size() == 0 ) {
				d.addText("Least visited: ["+ leastVisitedCell.row+","+leastVisitedCell.column+"] Value "+leastVisitedCell.value );
				return leastVisitedCell;
			}
			Cell c = Collections.min( pool );
			pool.remove(c);
			return c;
		}
		void setLastUnsuccesfulTrial( Cell c )
		{
			lastTrial = c;
		}
		Cell getLastTrial( )
		{
			return lastTrial;
		}
		@Override
		public void cloneData() {
			pool = new HashSet<>( trialCells );
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
	/**
	 *   "_SolutionSpace" is the target of the decision space. It is depicted
	 *  by a couple of indices, namely (i,j), that are the coordinates of the cells.
	 *  Hence we have:
	 *  	f^-1(i,j) -> (x,y,z)
	 *  which are the row, column, region associated to each cell. The map f is
	 *   "bijective", as excepted, thanks to the object Cell.
	 *  This "space" can only be in "continuing expansion", provided the "play-out"
	 *  permits that.
	 */
	class _SolutionSpace implements _Space, _Functor
	{
		int SolutioncellFilledCount = 0;
		Cell[][] solutionCells;
		HashSet<Cell> unsuccessfulTrials = new HashSet<>( MAX_POSSIBLE_SCORE );
		_SolutionSpace() {
			solutionCells = new Cell[9][9];
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


	void printSets()
	{
		d.addText(" ");
		for( int i = 0; i < 9; i++ )
			d.addText( "Ligne "+i+": "+rows.get( i )+" complémentaire: "+getComplement(rows.get( i )));
		d.addText(" ");
		for( int i = 0; i < 9; i++ )
			d.addText( "Colonne "+i+": "+columns.get( i )+" complémentaire: "+getComplement(columns.get( i )));
		d.addText(" ");
		for( int i = 0; i < 9; i++ )
			d.addText( "Régions: "+i+": "+regions.get( i )+" complémentaire: "+getComplement(regions.get( i )) );
	}

	void printResults()
	{
		d.addText( "Nombre d'éléments arbre: "+ trialCells.size() );
	}
}


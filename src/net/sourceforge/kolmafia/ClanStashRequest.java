/**
 * Copyright (c) 2005, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia development team" nor the names of
 *      its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClanStashRequest extends KoLRequest
{
	private int moveType;
	private Object [] items;
	private List source, destination;

	public static final int REFRESH_ONLY = 0;
	public static final int ITEMS_TO_STASH = 1;
	public static final int MEAT_TO_STASH = 2;
	public static final int STASH_TO_ITEMS = 3;

	public ClanStashRequest( KoLmafia client )
	{
		super( client, "clan_stash.php" );
		this.items = null;
		this.moveType = REFRESH_ONLY;
	}

	/**
	 * Constructs a new <code>ClanStashRequest</code>.
	 * @param	client	The client to be notified of the results
	 * @param	amount	The amount of meat involved in this transaction
	 */

	public ClanStashRequest( KoLmafia client, int amount )
	{
		super( client, "clan_stash.php" );
		addFormField( "action", "contribute" );
		addFormField( "howmuch", String.valueOf( amount ) );

		this.items = null;
		this.moveType = MEAT_TO_STASH;
	}

	/**
	 * Constructs a new <code>ClanStashRequest</code>.
	 * @param	client	The client to be notified of the results
	 * @param	items	The list of items involved in the request
	 */

	public ClanStashRequest( KoLmafia client, Object [] items, int moveType )
	{
		super( client, "clan_stash.php" );

		addFormField( "pwd", client.getPasswordHash() );
		addFormField( "action", (moveType == ITEMS_TO_STASH) ? "addgoodies" : "takegoodies" );

		this.items = items;
		this.moveType = moveType;

		source = client.getInventory();
		destination = new ArrayList();
	}

	public int getMoveType()
	{	return moveType;
	}

	public List getItems()
	{
		List itemList = new ArrayList();

		if ( items == null )
			return itemList;

		for ( int i = 0; i < items.length; ++i )
			itemList.add( items[i] );

		return itemList;
	}

	/**
	 * Executes the <code>ClanStashRequest</code>.
	 */

	public void run()
	{
		switch ( moveType )
		{
			case REFRESH_ONLY:
				updateDisplay( DISABLED_STATE, "Retrieving stash list..." );
				super.run();
				parseStash();
				updateDisplay( ENABLED_STATE, "Stash list retrieved." );
				break;

			case MEAT_TO_STASH:
				updateDisplay( DISABLED_STATE, "Attempting clan donation..." );
				super.run();
				parseStash();
				updateDisplay( ENABLED_STATE, "Clan donation attempt complete." );
				break;

			case ITEMS_TO_STASH:
			case STASH_TO_ITEMS:

				if ( items.length == 1 )
					updateDisplay( DISABLED_STATE, "Moving " + items[0] +
						(moveType == ITEMS_TO_STASH ? " to the stash..." : " to your bag...") );

				stash();
				parseStash();

				if ( items.length > 0 )
					updateDisplay( ENABLED_STATE, "Successfully moved " + items[ items.length - 1 ] +
						(moveType == ITEMS_TO_STASH ? " to the stash." : " to your bag.") );

				break;
		}
	}

	private void stash()
	{
		// First, check to see how many items are to be
		// placed in the stash - if there's too many,
		// then you'll need to break up the request

		if ( items == null || items.length == 0 )
			return;

		if ( items.length > 1 )
		{
			Object [] itemHolder = new Object[1];
			for ( int i = 0; i < items.length; ++i )
			{
				itemHolder[0] = items[i];
				(new ClanStashRequest( client, itemHolder, moveType )).run();
			}

			return;
		}

		AdventureResult result = (AdventureResult) items[0];
		int itemID = result.getItemID();

		if ( itemID != -1 )
		{
			addFormField( "whichitem", "" + itemID );
			addFormField( "quantity", "" + result.getCount() );

			super.run();

			if ( moveType == ITEMS_TO_STASH )
				client.processResult( result.getNegation() );
			else
				client.processResult( result );
		}
	}

	private void parseStash()
	{
		// In the event that the request was broken up into pieces, there's nothing to look
		// at.  Return from the function call.

		if ( responseText == null || responseText.length() == 0 )
			return;

		List stashContents = client.getClanManager().getStash();

		// Start with an empty list
		stashContents.clear();
		Matcher stashMatcher = Pattern.compile( "<form name=takegoodies.*?</select>" ).matcher( responseText );

		// If there's nothing inside the goodies hoard,
		// return because there's nothing to parse

		if ( !stashMatcher.find() )
			return;

		int lastFindIndex = 0;
		Pattern qtyPattern = Pattern.compile( "\\(([\\d,]+)\\)" );
		Matcher optionMatcher = Pattern.compile( "<option value=([\\d]+)>(.*?)</option>" ).matcher( stashMatcher.group() );

		while ( optionMatcher.find( lastFindIndex ) )
		{
			try
			{
				lastFindIndex = optionMatcher.end();
				int itemID = df.parse( optionMatcher.group(1) ).intValue();

				String itemString = optionMatcher.group(2);

				if ( TradeableItemDatabase.getItemName( itemID ) == null )
				{
					TradeableItemDatabase.registerItem( client, itemID, itemString.indexOf( "(" ) == -1 ? itemString :
						itemString.substring( 0, itemString.indexOf( "(" ) ).trim() );
				}

				AdventureResult result;
				if ( itemString.indexOf( "(" ) == -1 )
					result = new AdventureResult( itemID, 1 );
				else
				{
					Matcher qtyMatcher = qtyPattern.matcher( itemString.substring( itemString.indexOf( "(" ) ) );
					result = new AdventureResult( itemID, qtyMatcher.find() ? df.parse( qtyMatcher.group(1) ).intValue() : 1 );
				}

				AdventureResult.addResultToList( stashContents, result );
			}
			catch ( Exception e )
			{
				// If an exception occurs during the parsing, just
				// continue after notifying the LogStream of the
				// error.  This could be handled better, but not now.

				logStream.println( e );
				e.printStackTrace( logStream );
			}
		}
	}
}

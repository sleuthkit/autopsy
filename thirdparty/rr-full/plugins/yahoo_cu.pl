#-----------------------------------------------------------
# yahoo_cu.pl
#   Yahoo Messenger parser (HKCU)
#
# Change history
#   20101219 [fpi] % created
#   20101219 [fpi] % first version
#   20101221 [fpi] * added refences, minor changes
#   20110830 [fpi] + banner, no change to the version number
#
# References
#	Registry Quick Find Chart - AccessData
#	Bruce Long Internet Forensics - Yahoo Instant Messenger
#   http://www.xssed.com/article/14/Paper_In-Depth_Analysis_of_Yahoo_Authentication_Schemes/
# 
#
# NOTE: missing to manage the following
#       - IMVironments (global and user)
#       - user\Cache (missing informations about it)
#       - user\Chat
#
# copyright 2011 F. Picasso <francesco.picasso@gmail.com>
#-----------------------------------------------------------
package yahoo_cu;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20101219);

sub getConfig{return %config}
sub getShortDescr {
	return "Yahoo Messenger parser";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Registry Quick Find Chart - AccessData" => 
	            "http://www.accessdata.com/media/en_us/print/papers/wp.Registry_Quick_Find_Chart.en_us.pdf",
				"In-Depth Analysis of Yahoo! Authentication Schemes" =>
				"http://www.xssed.com/article/14/Paper_In-Depth_Analysis_of_Yahoo_Authentication_Schemes/",
				"Bruce Long" =>
				"Internet Forensics - Yahoo Instant Messenger");
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg( "Launching yahoo_cu v.".$VERSION );
    ::rptMsg("yahoo_cu v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner
	
	my $reg = Parse::Win32Registry->new( $ntuser );
	my $root_key = $reg->get_root_key;

	my $path = 'Software\\Yahoo\\pager';
	my $key;
	
	if ( $key = $root_key->get_subkey( $path ) ) {
				
		::rptMsg( "LastWrite Time ".gmtime($key->get_timestamp())." (UTC) ".$key->get_name() );
		
		my %summary = ( 'Version' 				=> '',
						'Launch on Startup' 	=> '',
						'Connection Server' 	=> '',
						'Last Login UserName' 	=> '',
						'Last Local IP' 		=> '',
						'AutoLogin' 			=> '',
						'Save Password' 		=> '',
						'Encrypted Password'	=> '',
						'Yahoo Token'			=> ''
		);
				
		my @vals = $key->get_list_of_values();
		if ( ( scalar @vals ) > 0 ) {
			foreach my $val ( @vals ) {
				_fillSummary( $val, \%summary );
			}
			_printSummary( \%summary );
		}
		else {
			::rptMsg( $key->get_name()." has no values." );
			::logMsg( $key->get_name()." has no values." );
		}
		
		if ( $key = $key->get_subkey( 'profiles' ) ) {
			::rptMsg( "\n  LastWrite Time ".gmtime($key->get_timestamp())." (UTC) ".$key->get_name() );
			my $tmp;
			my $cu;
			my $sbk;
			my @badusers;
			my @users;
			my @subkeys = $key->get_list_of_subkeys();
			if ( ( scalar @subkeys ) > 0 ) {
				# finding users and bad users (bad logins)
				# 1- if subkey has no subkeys, is not a user
				# 2- if subkey has 3 or less subkeys, probably it's a bad user
				# 3- if subkey has >3 subkeys, probably it's a good user	
				foreach $sbk ( @subkeys ) {
					my @subkeys2 = $sbk->get_list_of_subkeys();
					$tmp = scalar @subkeys2;
					if ( $tmp > 0 && $tmp < 4 ) {
						push( @badusers, $sbk );
					}
					elsif ( $tmp >= 4 ) {
						push( @users, $sbk );
					}
				}
			}
						
			# got users and badusers
			::rptMsg( "  Found ".scalar @users." users." );
			::rptMsg( "  Found ".scalar @badusers." bad users logins." );
			::rptMsg( "" );
			
			# let's parse users
			my $spaces = '  ';
			if ( scalar @users ) {
				foreach $cu ( @users ) {
					::rptMsg( $spaces."USER: ".$cu->get_name() );
					::rptMsg( $spaces."LastWrite Time ".gmtime($cu->get_timestamp())." (UTC) ".$cu->get_name() );
					_parseUserValues( \$cu, $spaces );
					$spaces = '    ';
					_parseAlerts( \$cu, $spaces );
					_parseArchives( \$cu, $spaces );
					_parseFriendIcons( \$cu, $spaces );
					_parseFT( \$cu, $spaces );
				}
			}	

			# let's parse badusers
			::rptMsg( "" );
			if ( scalar @badusers ) {
				foreach $cu ( @badusers ) {
					::rptMsg( "  BAD LOGIN USER: ".$cu->get_name() );
					::rptMsg( "  LastWrite Time ".gmtime($cu->get_timestamp())." (UTC) ".$cu->get_name() );
					if ( $sbk = $cu->get_subkey( 'Alerts' ) ) {
						::rptMsg( "    LastWrite Time ".gmtime($sbk->get_timestamp())." (UTC) ".$sbk->get_name() );
						_printExpectedValue( \$sbk, 'Total Login Tries', '    ' );
					}
					else {
						::rptMsg( "    Missing expected 'Alerts' subkey" );
					}
					::rptMsg( "" );
				}
			}		
		}
		else {
			::rptMsg( "No profiles found." );
			::logMsg( "No profiles found." );
		}
	}
	else {
		::rptMsg( $path." not found." );
		::logMsg( $path." not found." );
	}
}

#------------------------------------------------------------------------------

sub _parseUserValues() {
	my @vals = ${$_[0]}->get_list_of_values();
	foreach my $v (@vals) {
		my $val = $v->get_name();
		my $data = $v->get_data();
		if ( $val eq 'All Identities' ) {
			::rptMsg( $_[1].$val." = ".$data );
		}
		elsif ( $val eq 'Selected Identities' ) {
			::rptMsg( $_[1].$val." = ".$data );
		}
		elsif ( $val eq 'pref' ) {
			::rptMsg( $_[1].$val." = ".$data );
		}
		elsif ( $val eq 'yinsider date' ) {
			::rptMsg( $_[1].$val." = ".gmtime($data)." (UTC)" );
		}		
	}	
}

#------------------------------------------------------------------------------

sub _parseAlerts() {
	if ( my $local = ${$_[0]}->get_subkey( 'Alerts' ) ) {
		::rptMsg( $_[1]."LastWrite Time ".gmtime( $local->get_timestamp())." (UTC) ".$local->get_name() );
		_printExpectedValue( \$local, 'Total Login Tries', $_[1] );
		_printExpectedValue( \$local, 'Total Disconnects', $_[1] );
	}
	else {
		::rptMsg( $_[1]."Missing expected 'Alerts' subkey." );
	}
}

#------------------------------------------------------------------------------

sub _parseArchives() {
	my $got1;
	my $got2;
	my $val1;
	my $val2;
	my $str;
	if ( my $local = ${$_[0]}->get_subkey( 'Archive' ) ) {
		::rptMsg( $_[1]."LastWrite Time ".gmtime( $local->get_timestamp())." (UTC) ".$local->get_name() );
		# messages archive policies
		( $got1, $val1 ) = _printExpectedValue( \$local, 'Enabled', $_[1] );
		( $got2, $val2 ) = _printExpectedValue( \$local, 'Autodelete', $_[1] );
		
		if ( $got1 && $got2 ) {
			if ( $val1 != 0 ) {
				$str = "Messages archiving is ENABLED. "
			}
			else {
				$str = "Messages archiving is NOT enabled. "
			}
			if ( $val2 != 0 ) {
				$str .= "Archived messages are DELETED automatically on user sign-off.";
			}
			else {
				$str .= "Archived messages are NOT automatically deleted on user sign-off.";
			}
			::rptMsg( $_[1]."NOTE: ".$str );
		}
		else {
			::rptMsg( $_[1]."NOTE: cannot determine archived messages policy due to missing values." );
		}
		# voice call archive policies
		( $got1, $val1 ) = _printExpectedValue( \$local, 'CallHistoryEnabled', $_[1] );
		( $got2, $val2 ) = _printExpectedValue( \$local, 'CallHistoryAutodelete', $_[1] );
		
		if ( $got1 && $got2 ) {
			if ( $val1 != 0 ) {
				$str = "Call history archiving is ENABLED. "
			}
			else {
				$str = "Call history archiving is NOT enabled. "
			}
			if ( $val2 != 0 ) {
				$str .= "Call history is DELETED automatically on user sign-off.";
			}
			else {
				$str .= "Call history is NOT automatically deleted on user sign-off.";
			}
			::rptMsg( $_[1]."NOTE: ".$str );
		}
		else {
			::rptMsg( $_[1]."NOTE: cannot determine call history policy due to missing values." );
		}
		
	}
	else {
		::rptMsg( $_[1]."Missing expected 'Archive' subkey." );
	}
}

#------------------------------------------------------------------------------

sub _parseFriendIcons() {
	if ( my $local = ${$_[0]}->get_subkey( 'FriendIcons' ) ) {
		::rptMsg( $_[1]."LastWrite Time ".gmtime( $local->get_timestamp())." (UTC) ".$local->get_name() );
		_printExpectedValue( \$local, 'Checksum', $_[1] );
		_printExpectedValue( \$local, 'LastDir', $_[1] );
		_printExpectedValue( \$local, 'Path', $_[1] );
	}
	else {
		::rptMsg( $_[1]."Missing expected 'FriendIcons' subkey." );
	}
}

#------------------------------------------------------------------------------

sub _parseFT() {
	if ( my $local = ${$_[0]}->get_subkey( 'FT' ) ) {
		::rptMsg( $_[1]."LastWrite Time ".gmtime( $local->get_timestamp())." (UTC) ".$local->get_name() );
		_printExpectedValue( \$local, 'LastSaveLocation', $_[1] );
		_printExpectedValue( \$local, 'LastSendLocation', $_[1] );
	}
	else {
		::rptMsg( $_[1]."Missing expected 'FT' subkey." );
	}
}

#------------------------------------------------------------------------------

sub _printExpectedValue() {
	my $got;
	my $val;
	my $tmp;
	if ( $tmp = ${$_[0]}->get_value( $_[1] ) ) {
		$val = $tmp->get_data();
		::rptMsg( $_[2].$_[1]." = ".$val );
		$got = 1;
	}
	else {
		::rptMsg( $_[2]."Missing expected value '".$_[1]."'" );
		$got = 0;
	}
	return ( $got, $val );
}

#------------------------------------------------------------------------------

sub _fillSummary() {
	my $tmp = $_[0]->get_name();
	if    ( $tmp eq 'Version' ) { ${$_[1]}{'Version'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'Launch on Startup' ) { ${$_[1]}{'Launch on Startup'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'ConnServer' ) { ${$_[1]}{'Connection Server'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'Yahoo! User ID' ) { ${$_[1]}{'Last Login UserName'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'CurrentUserLocalIP' ) { ${$_[1]}{'Last Local IP'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'Auto Login' ) { ${$_[1]}{'AutoLogin'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'Save Password' ) { ${$_[1]}{'Save Password'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'EOptions string' ) { ${$_[1]}{'Encrypted Password'} = $_[0]->get_data(); }
	elsif ( $tmp eq 'ETS' ) { ${$_[1]}{'Yahoo Token'} = $_[0]->get_data(); }
}

#------------------------------------------------------------------------------

sub _printSummary() {
	::rptMsg( '  Version             = '.${$_[0]}{'Version'} );
	::rptMsg( '  Launch on Startup   = '.${$_[0]}{'Launch on Startup'} );
	::rptMsg( '  Connection Server   = '.${$_[0]}{'Connection Server'} );
	::rptMsg( '  Last Login UserName = '.${$_[0]}{'Last Login UserName'} );
	::rptMsg( '  Last Local IP       = '.${$_[0]}{'Last Local IP'} );
	::rptMsg( '  AutoLogin           = '.${$_[0]}{'AutoLogin'} );
	::rptMsg( '  Save Password       = '.${$_[0]}{'Save Password'} );
	::rptMsg( '  Encrypted Password  = '.${$_[0]}{'Encrypted Password'} );
	::rptMsg( '  Yahoo Token         = '.${$_[0]}{'Yahoo Token'} );

	if ( ${$_[0]}{'Encrypted Password'} ne '' ) {
		::rptMsg( "  NOTE: detected encrypted password.\nYou should be able to decrypt the password." );
	}
	elsif ( ${$_[0]}{'Yahoo Token'} ne '' ) {
		::rptMsg( "  NOTE: detected Yahoo ETS Token. You should be able to impersonificate the user ");
		::rptMsg( "        using the Yahoo Token but you cannot obtain the cleartext password." );
	}
	else {
		::rptMsg( "  NOTE: you should not be able to obtain the password." );
	}
}

#------------------------------------------------------------------------------

1;
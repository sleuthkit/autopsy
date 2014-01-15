#-----------------------------------------------------------
# startmenuinternetapps_cu.pl
#   Start Menu Internet Applications settings (HKCU) parser
#
# Change history
#   20100906 [fpi] % created
#   20101219 [fpi] % first version
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   http://msdn.microsoft.com/en-us/library/dd203067(VS.85).aspx
# 
# copyright 2010 F. Picasso, francesco.picasso@gmail.com
#-----------------------------------------------------------
package startmenuinternetapps_cu;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20101219);

sub getConfig{return %config}
sub getShortDescr {
	return "Start Menu Internet Applications info current user";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("How to Register an Internet Browser or E-mail Client With the Windows Start Menu" => 
	            "http://msdn.microsoft.com/en-us/library/dd203067(VS.85).aspx");
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg( "Launching startmenuinternetapps_cu v.".$VERSION );
    ::rptMsg("startmenuinternetapps_cu v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner
	
	my $reg = Parse::Win32Registry->new( $ntuser );
	my $root_key = $reg->get_root_key;

	my $path = 'Software\\Clients';
	my $key;
	
	if ( $key = $root_key->get_subkey( $path ) ) {
		::rptMsg( "Start Menu Internet Applications\n" );

		my @subkeys = $key->get_list_of_subkeys();	
		if ( ( scalar @subkeys ) > 0 ) {
		
			foreach my $sbk ( @subkeys ) {
				my $tmp = $sbk->get_name();
				::rptMsg( $tmp." [".gmtime( $sbk->get_timestamp() )." (UTC)]" );

				if ( $tmp eq "StartMenuInternet" ) {
					::rptMsg( "NOTE: default Internet Browser client key" );
				}
				elsif ( $tmp eq "Mail" ) {
					::rptMsg( "NOTE: default Mail client key" );
				}
				
				my @vals = $sbk->get_list_of_values();
	
				if ( ( scalar @vals ) > 0 ) {
					foreach my $val ( @vals ) {
						$tmp = $val->get_name();
						if ( $tmp eq "" ) {
							$tmp = "(default)";
						}
						::rptMsg( $tmp." -> ".$val->get_data()."\n" );
					}
				}
				else {
					::rptMsg( $sbk->get_name()." has no values." );
					::logMsg( $sbk->get_name()." has no values." );
				}
			}
		}
		else {
			::rptMsg( $key->get_name()." has no subkeys." );
			::logMsg( $key->get_name()." has no subkeys." );
		}
	}
	else {
		::rptMsg( $path." not found. Check the same path in HKLM" );
		::logMsg( $path." not found. Check the same path in HKLM" );
	}
}

1;
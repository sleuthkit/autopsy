#-----------------------------------------------------------
# yahoo_lm.pl
#   Yahoo Messenger parser (HKLM)
#
# Change history
#   20101219 [fpi] % created
#   20101219 [fpi] % first version
#   20110830 [fpi] + banner, no change to the version number
#
# References
# 
# copyright 2011 F. Picasso <francesco.picasso@gmail.com>
#-----------------------------------------------------------
package yahoo_lm;
use strict;

my %config = (hive          => "SOFTWARE",
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
	my %refs = ("Access Data Registry Quick Reference" => 
	            "google it!");
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg( "Launching yahoo_lm v.".$VERSION );
    ::rptMsg("yahoo_lm v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner
	
	my $reg = Parse::Win32Registry->new( $ntuser );
	my $root_key = $reg->get_root_key;

	my $path = 'Yahoo';
	my $key;
	
	if ( $key = $root_key->get_subkey( $path ) ) {
		::rptMsg( "Searching for Yahoo Messenger installation..." );

		my $found = 0;
		my @subkeys = $key->get_list_of_subkeys();	
		if ( ( scalar @subkeys ) > 0 ) {
		
			foreach my $sbk ( @subkeys ) {
				my $tmp = $sbk->get_name();
				
				if ( $tmp eq "pager" ) {
					$found++;
					::rptMsg( "... 'pager' key is present." );
					::rptMsg( "[".gmtime( $sbk->get_timestamp() )." (UTC)] ".$tmp );
				
					my @vals = $sbk->get_list_of_values();
	
					if ( ( scalar @vals ) > 0 ) {
						foreach my $val ( @vals ) {
							$tmp = $val->get_name();
							if ( $tmp eq "ProductVersion" ) {
								$found++;
								::rptMsg( $tmp." -> ".$val->get_data() );
							}
						}
						if ( $found == 1 ) {
							::rptMsg( "unable to get 'ProductVersion' value." );
						}
					}
				}
			}
			if ( $found == 0 ) {
				::rptMsg( "No Yahoo Messenger installation detected." );
			}
		}
		else {
			::rptMsg( $key->get_name()." has no subkeys." );
			::logMsg( $key->get_name()." has no subkeys." );
		}
	}
	else {
		::rptMsg( $path." not found." );
		::logMsg( $path." not found." );
	}
}
1;
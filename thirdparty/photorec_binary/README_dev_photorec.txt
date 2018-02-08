PhotoRec - Theory of operation:

Carvers are plugable. Each carver consists of:

struct file_hint_t - describes extension, name, max size, enable by default, etc.
file_enable_t list_file_enable[] - array with all file hints and whether enabled or not.


phmain.c - Contains the main() and driver logic for photorec.
 
main():
 - reads parameters
 - scans for available devices
 - parses the HD (or image)
 - resets the list of which file carvers are enabled
 - Initializes ncurses
 - Calls do_curses_photorec() (in pdisksel.c)
 - shuts down ncurses


pdiskseln.c - 
 int do_curses_photorec(struct ph_param *params, struct ph_options *options, const list_disk_t *list_disk)
 - Implements Disk selection
 - May call photorec_disk_selection_ncurses()
 - Eventually call menu_photorec() in ppartseln.c

ppartseln.c -
  void menu_photorec(struct ph_param *params, struct ph_options *options, alloc_data_t*list_search_space)
 - Implements Partition selection and Search/Options/File Opt/Geometry/Quit menu
 - Search Option call photorec()

phrecn.c -
 int photorec(struct ph_param *params, const struct ph_options *options, alloc_data_t *list_search_space)
 - runs multiple passes until status==STATUS_QUIT
 - calls photorec_mkdir() to actually make the output directory
 - calls photorec_find_blocksize() to find the block size
 - calls photorec_aux() to do the recovery
 - may call photorec_bf() to recover more files by brute-force method

src/psearchn.c - 
  pstatus_t photorec_aux(struct ph_param *params, const struct ph_options *options, alloc_data_t *list_search_space)
  - calls file_finish2() when recovery of a file is finished (ie. a new file has been found)
  - calls file_recovery_aborted() if the user stops the recovery or there is not enough space on the destination

photorec.c: -
 int file_finish_bf(file_recovery_t *file_recovery, struct ph_param *params, alloc_data_t *list_search_space)
 - called when recovery of a file is done by
   - photorec_bf()
   - photorec_bf_pad()
   - photorec_bf_frag()
   - photorec_bf_aux()

photorec.c
 pfstatus_t file_finish2(file_recovery_t *file_recovery, struct ph_param *params, const int paranoid, alloc_data_t *list_search_space)
 - called when a file is done by
 - photorec_aux() (in three places)
 


#! /usr/bin/python
import sys

# Example of importing packages which may or may not exist, pretty nice to use in a "setup" script.
# Pip is required for this to work.
try:
    import pandas as pd
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", 'pandas'])
finally:
    import pandas as pd

print('Arguments: ' + sys.argv[1])
print('Hello world!')

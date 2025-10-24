cd /Users/wesleylu/Desktop/cs4240-project-2
chmod +x build.sh
./build.sh

cd /Users/wesleylu/Desktop/cs4240-project-2
chmod +x run.sh
./run.sh public_test_cases/quicksort/quicksort.ir --greedy

cd /Users/wesleylu/Desktop/cs4240-project-2
chmod +x test/run_public_tests.sh
./test/run_public_tests.sh test/out_quicksort_greedy.s quicksort
./test/run_public_tests.sh test/out_prime_naive.s prime
./test/run_public_tests.sh test/out_quicksort_naive.s quicksort
./test/run_public_tests.sh test/out_prime_greedy.s prime
